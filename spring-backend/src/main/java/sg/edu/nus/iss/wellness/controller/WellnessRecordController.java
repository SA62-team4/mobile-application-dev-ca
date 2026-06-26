package sg.edu.nus.iss.wellness.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.wellness.dto.WellnessDtos;
import sg.edu.nus.iss.wellness.error.ApiException;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.WellnessRecord;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;
import sg.edu.nus.iss.wellness.service.CurrentUserService;
import sg.edu.nus.iss.wellness.service.DtoMapper;

import java.time.LocalDate;
import java.util.List;

/**
 * Provides authenticated wellness record CRUD APIs.
 *
 * @author SA62 Team
 */
@RestController
@RequestMapping("/api/wellness-records")
public class WellnessRecordController {
    private final WellnessRecordRepository records;
    private final CurrentUserService currentUserService;

    public WellnessRecordController(WellnessRecordRepository records, CurrentUserService currentUserService) {
        this.records = records;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WellnessDtos.WellnessRecordResponse create(@Valid @RequestBody WellnessDtos.WellnessRecordRequest request) {
        AppUser user = currentUserService.requireCurrentUser();
        WellnessRecord record = new WellnessRecord();
        record.setUser(user);
        apply(record, request);
        return DtoMapper.wellness(records.save(record));
    }

    @GetMapping
    public List<WellnessDtos.WellnessRecordResponse> list(@RequestParam(required = false) LocalDate from,
                                                          @RequestParam(required = false) LocalDate to) {
        AppUser user = currentUserService.requireCurrentUser();
        List<WellnessRecord> result = (from != null && to != null)
                ? records.findByUserAndRecordDateBetweenOrderByRecordDateDesc(user, from, to)
                : records.findByUserOrderByRecordDateDesc(user);
        return result.stream().map(DtoMapper::wellness).toList();
    }

    @GetMapping("/{id}")
    public WellnessDtos.WellnessRecordResponse get(@PathVariable Long id) {
        AppUser user = currentUserService.requireCurrentUser();
        return records.findByIdAndUser(id, user)
                .map(DtoMapper::wellness)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wellness record not found"));
    }

    @PutMapping("/{id}")
    public WellnessDtos.WellnessRecordResponse update(@PathVariable Long id,
                                                      @Valid @RequestBody WellnessDtos.WellnessRecordRequest request) {
        AppUser user = currentUserService.requireCurrentUser();
        WellnessRecord record = records.findByIdAndUser(id, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wellness record not found"));
        apply(record, request);
        return DtoMapper.wellness(records.save(record));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        AppUser user = currentUserService.requireCurrentUser();
        WellnessRecord record = records.findByIdAndUser(id, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wellness record not found"));
        records.delete(record);
    }

    private void apply(WellnessRecord record, WellnessDtos.WellnessRecordRequest request) {
        record.setRecordDate(request.recordDate());
        record.setSleepHours(request.sleepHours());
        record.setExerciseType(request.exerciseType());
        record.setExerciseMinutes(request.exerciseMinutes());
        record.setMoodScore(request.moodScore());
        record.setNotes(request.notes());
    }
}

