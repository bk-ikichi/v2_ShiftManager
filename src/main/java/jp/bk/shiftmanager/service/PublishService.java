package jp.bk.shiftmanager.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.repository.PublishedDateRepository;
import jp.bk.shiftmanager.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 確定シフトの公開（管理者）。公開の取り消しはない */
@Service
@RequiredArgsConstructor
public class PublishService {

    /** 一度に公開できる日数（画面の選択肢は最大31日） */
    private static final int MAX_DAYS = 31;

    private final ShiftRepository shiftRepository;
    private final PublishedDateRepository publishedDateRepository;

    /** その日を公開する。シフトが登録されていない日は公開できない */
    @Transactional
    public void publishDay(String dateText) {
        LocalDate date = parseDate(dateText);
        if (shiftRepository.findDates(date, date).isEmpty()) {
            throw new BusinessException("シフトが登録されていないため公開できません");
        }
        publishedDateRepository.publish(date);
    }

    /**
     * 期間内のシフトが登録されている日を公開し、公開しなかった日（シフト未登録）を返す。
     * 1日も公開できない場合は入力エラー
     */
    @Transactional
    public List<LocalDate> publishRange(String fromText, String toText) {
        LocalDate from = parseDate(fromText);
        LocalDate to = parseDate(toText);
        if (to.isBefore(from)) {
            throw new BusinessException("終了日は開始日以降の日付を選択してください");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BusinessException("一度に公開できるのは" + MAX_DAYS + "日分までです");
        }
        Set<LocalDate> withShifts = new HashSet<>(shiftRepository.findDates(from, to));
        if (withShifts.isEmpty()) {
            throw new BusinessException("期間内にシフトが登録されている日がないため公開できません");
        }
        List<LocalDate> skipped = new ArrayList<>();
        for (LocalDate date : from.datesUntil(to.plusDays(1)).toList()) {
            if (withShifts.contains(date)) {
                publishedDateRepository.publish(date);
            } else {
                skipped.add(date);
            }
        }
        return skipped;
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text);
        } catch (DateTimeParseException e) {
            throw new BusinessException("不正な日付です");
        }
    }
}
