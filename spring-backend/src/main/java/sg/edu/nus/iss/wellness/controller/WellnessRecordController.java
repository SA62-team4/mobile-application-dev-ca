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
    private static final String RECORD_NOT_FOUND = "Wellness record not found";

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
        WellnessRecord wellnessRecord = new WellnessRecord();
        wellnessRecord.setUser(user);
        apply(wellnessRecord, request);
        return DtoMapper.wellness(records.save(wellnessRecord));
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, RECORD_NOT_FOUND));
    }

    @PutMapping("/{id}")
    public WellnessDtos.WellnessRecordResponse update(@PathVariable Long id,
                                                      @Valid @RequestBody WellnessDtos.WellnessRecordRequest request) {
        AppUser user = currentUserService.requireCurrentUser();
        WellnessRecord wellnessRecord = records.findByIdAndUser(id, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, RECORD_NOT_FOUND));
        apply(wellnessRecord, request);
        return DtoMapper.wellness(records.save(wellnessRecord));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        AppUser user = currentUserService.requireCurrentUser();
        WellnessRecord wellnessRecord = records.findByIdAndUser(id, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, RECORD_NOT_FOUND));
        records.delete(wellnessRecord);
    }

    private void apply(WellnessRecord wellnessRecord, WellnessDtos.WellnessRecordRequest request) {
        wellnessRecord.setRecordDate(request.recordDate());
        wellnessRecord.setSleepHours(request.sleepHours());
        wellnessRecord.setExerciseType(request.exerciseType());
        wellnessRecord.setExerciseMinutes(request.exerciseMinutes());
        wellnessRecord.setMoodScore(request.moodScore());
        wellnessRecord.setNotes(request.notes());
    }
}

