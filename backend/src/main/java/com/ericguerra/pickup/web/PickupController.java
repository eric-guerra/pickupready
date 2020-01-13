package com.ericguerra.pickup.web;

import com.ericguerra.pickup.domain.PickupOrder;
import com.ericguerra.pickup.service.PickupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class PickupController {
    public record LineReq(@NotBlank String sku, @NotBlank String name, @Min(1) int qty, String aisle) {}
    public record PlaceReq(@NotBlank String orderNo, @NotBlank String storeId, @NotBlank String customerName,
                           String customerPhone, @NotEmpty List<@Valid LineReq> lines) {}
    public record PickReq(@Min(0) int found) {}
    public record SubReq(@NotBlank String sku, @NotBlank String name) {}
    public record DecideReq(boolean approved) {}
    public record StageReq(@NotBlank String location) {}
    public record CheckInReq(String spot) {}
    public record HandOffReq(@Pattern(regexp = "\\d{6}") String code) {}
    public record CancelReq(@NotBlank String reason) {}

    private final PickupService service;

    public PickupController(PickupService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> place(@Valid @RequestBody PlaceReq r) {
        var placed = service.place(r.orderNo(), r.storeId(), r.customerName(), r.customerPhone(),
                r.lines().stream().map(l -> new PickupService.NewLine(l.sku(), l.name(), l.qty(), l.aisle())).toList());
        // The code is returned once, to the commerce platform that emails it; it's never readable again.
        return Map.of("order", placed.order(), "pickupCode", placed.pickupCode());
    }

    @GetMapping("/orders/{no}") public PickupOrder get(@PathVariable String no) { return service.get(no); }
    @GetMapping("/stores/{store}/board") public List<PickupOrder> board(@PathVariable String store) { return service.board(store); }

    @PostMapping("/orders/{no}/claim")
    public PickupOrder claim(@PathVariable String no, @RequestHeader("X-Associate") String who) { return service.claim(no, who); }

    @PostMapping("/orders/{no}/lines/{line}/pick")
    public PickupOrder pick(@PathVariable String no, @PathVariable Long line, @Valid @RequestBody PickReq r, @RequestHeader("X-Associate") String who) {
        return service.recordPick(no, line, r.found(), who);
    }

    @PostMapping("/orders/{no}/lines/{line}/substitute")
    public PickupOrder substitute(@PathVariable String no, @PathVariable Long line, @Valid @RequestBody SubReq r, @RequestHeader("X-Associate") String who) {
        return service.proposeSubstitute(no, line, r.sku(), r.name(), who);
    }

    @PostMapping("/orders/{no}/lines/{line}/decision")
    public PickupOrder decide(@PathVariable String no, @PathVariable Long line, @RequestBody DecideReq r) {
        return service.decideSubstitute(no, line, r.approved());
    }

    @PostMapping("/orders/{no}/stage")
    public PickupOrder stage(@PathVariable String no, @Valid @RequestBody StageReq r, @RequestHeader("X-Associate") String who) {
        return service.stage(no, r.location(), who);
    }

    @PostMapping("/orders/{no}/check-in")
    public PickupOrder checkIn(@PathVariable String no, @RequestBody(required = false) CheckInReq r) {
        return service.checkIn(no, r == null ? null : r.spot());
    }

    @PostMapping("/orders/{no}/handoff")
    public PickupOrder handOff(@PathVariable String no, @Valid @RequestBody HandOffReq r, @RequestHeader("X-Associate") String who) {
        return service.handOff(no, r.code(), who);
    }

    @PostMapping("/orders/{no}/cancel")
    public PickupOrder cancel(@PathVariable String no, @Valid @RequestBody CancelReq r, @RequestHeader("X-Associate") String who) {
        return service.cancel(no, who, r.reason());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()); }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    ProblemDetail conflict(RuntimeException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()); }

    @ExceptionHandler(PickupService.WrongCodeException.class)
    ProblemDetail wrongCode(PickupService.WrongCodeException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        p.setProperty("remainingAttempts", e.remaining);
        return p;
    }

    @ExceptionHandler(PickupService.CodeLockedException.class)
    ProblemDetail locked(PickupService.CodeLockedException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, e.getMessage()); }
}
