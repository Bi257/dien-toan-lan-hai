package com.example.demo.service;

import com.example.demo.model.Booking;
import com.example.demo.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class BookingService {

    @Autowired
    private BookingRepository repository;

    private List<String> logs = new ArrayList<>();

    private String[] otherServers = {
            "https://hotel-booking-system-new.onrender.com",
            "https://demo2-75m2.onrender.com",
            "https://dientoanck.onrender.com"
    };

    private ConcurrentHashMap<String, Boolean> serverStatus = new ConcurrentHashMap<>();

    private int clock = 0;

    public BookingService() {
        for (String url : otherServers) {
            serverStatus.put(url, true);
        }
    }

    // ================= LAMPORT =================
    private synchronized int tick() {
        return ++clock;
    }

    private synchronized void updateClock(int received) {
        clock = Math.max(clock, received) + 1;
    }

    private String log(String phase, String msg) {
        String time = LocalTime.now().withNano(0).toString();
        return "[" + time + "] [L=" + clock + "] [" + phase + "] " + msg;
    }

    // ================= MAIN 4PC =================
    public void book(Booking b, String serverId) {
        tick();
        b.setLamportTime(clock);
        logs.add(log("CLIENT", "Nhận booking: " + b.getName()));

        CompletableFuture.runAsync(() -> {

            RestTemplate restTemplate = createRestTemplate();

            int total = otherServers.length + 1;
            int quorum = (total / 2) + 1;

            List<CompletableFuture<Boolean>> futures = new ArrayList<>();

            // ================= PHASE 1: CAN_COMMIT =================
            for (String url : otherServers) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        tick();
                        logs.add(log("P1", "CAN_COMMIT → " + url));
                        Boolean res = restTemplate.postForObject(url + "/api/canCommit", b, Boolean.class);
                        return Boolean.TRUE.equals(res);
                    } catch (Exception e) {
                        serverStatus.put(url, false);
                        logs.add(log("ERROR", "Server DOWN: " + url));
                        return false;
                    }
                }));
            }

            int ok = 1; // local luôn OK

            for (CompletableFuture<Boolean> f : futures) {
                if (f.join())
                    ok++;
            }

            if (ok < quorum) {
                tick();
                logs.add(log("P1", "ABORT - Không đủ quorum (" + ok + "/" + total + ")"));
                return;
            }

            tick();
            logs.add(log("P1", "QUORUM OK (" + ok + "/" + total + ")"));

            // ================= PHASE 2: PRE_COMMIT =================
            for (String url : otherServers) {
                CompletableFuture.runAsync(() -> {
                    try {
                        tick();
                        logs.add(log("P2", "PRE_COMMIT → " + url));
                        restTemplate.postForObject(url + "/api/preCommit", b, Boolean.class);
                    } catch (Exception ignored) {
                    }
                });
            }

            // ================= PHASE 3: WAIT / NON-BLOCKING =================
            try {
                Thread.sleep(1000); // giả lập delay
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            tick();
            logs.add(log("P3", "TIMEOUT → tự quyết định (non-blocking)"));

            // ================= PHASE 4: DO_COMMIT =================
            repository.save(b);
            tick();
            logs.add(log("P4", "COMMIT LOCAL"));

            for (String url : otherServers) {
                CompletableFuture.runAsync(() -> {
                    try {
                        restTemplate.postForObject(url + "/api/doCommit", b, String.class);
                    } catch (Exception ignored) {
                    }
                });
            }
        });
    }

    // ================= API HANDLER =================
    public boolean canCommit(Booking b) {
        updateClock(b.getLamportTime());
        tick();
        logs.add(log("P1", "Nhận CAN_COMMIT"));
        return true;
    }

    public boolean preCommit(Booking b) {
        updateClock(b.getLamportTime());
        tick();
        logs.add(log("P2", "Nhận PRE_COMMIT"));
        return true;
    }

    public void doCommit(Booking b) {
        updateClock(b.getLamportTime());
        repository.save(b);
        tick();
        logs.add(log("P4", "Nhận DO_COMMIT → lưu DB"));
    }

    // ================= UTIL =================
    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    public List<String> getLogs() {
        return logs;
    }

    public ConcurrentHashMap<String, Boolean> getServerStatus() {
        return serverStatus;
    }
}