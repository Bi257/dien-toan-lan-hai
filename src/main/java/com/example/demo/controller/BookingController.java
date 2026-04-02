package com.example.demo.controller;

import com.example.demo.model.Booking;
import com.example.demo.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BookingController {

    @Autowired
    private BookingService service;

    @Value("${server.id}")
    private String serverId;

    // ================= CLIENT =================
    @PostMapping("/book")
    public String book(@RequestBody Booking b) {
        service.book(b, serverId);
        return "Booking đang xử lý (4PC + Quorum)...";
    }

    // ================= PHASE 1 =================
    @PostMapping("/canCommit")
    public boolean canCommit(@RequestBody Booking b) {
        return service.canCommit(b);
    }

    // ================= PHASE 2 =================
    @PostMapping("/preCommit")
    public boolean preCommit(@RequestBody Booking b) {
        return service.preCommit(b);
    }

    // ================= PHASE 4 =================
    @PostMapping("/doCommit")
    public String doCommit(@RequestBody Booking b) {
        service.doCommit(b);
        return "COMMIT OK";
    }

    // ================= DEBUG =================
    @GetMapping("/log")
    public List<String> logs() {
        return service.getLogs();
    }

    @GetMapping("/status")
    public Object status() {
        return service.getServerStatus();
    }
}