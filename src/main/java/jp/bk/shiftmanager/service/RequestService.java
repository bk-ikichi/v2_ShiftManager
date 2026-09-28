package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.RequestCycleView;
import jp.bk.shiftmanager.dto.RequestDayView;
import jp.bk.shiftmanager.dto.RequestMonthView;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.RequestDayForm;
import jp.bk.shiftmanager.form.RequestMonthForm;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import jp.bk.shiftmanager.repository.CycleUnavailableRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.util.Cycle;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.RequestNote;
import jp.bk.shiftmanager.util.TimeRange;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** スタッフ本人によるシフト希望の申請 */
@Service
@RequiredArgsConstructor
public class RequestService {

    private static final String INVALID_DATE = "不正な日付です";

    private final Clock clock;
    private final AppSettingRepository appSettingRepository;
    private final ShiftRequestRepository shiftRequestRepository;
    private final CycleUnavailableRepository cycleUnavailableRepository;

    /** スタッフが申請できる月（今月〜翌々月） */
    public List<YearMonth> requestMonths() {
        YearMonth now = YearMonth.now(clock);
        return List.of(now, now.plusMonths(1), now.plusMonths(2));
    }

    /** 画面から指定された月。不正・範囲外なら今月 */
    public YearMonth resolveMonth(String text) {
        List<YearMonth> months = requestMonths();
        if (text != null) {
            try {
                YearMonth month = YearMonth.parse(text);
                if (months.contains(month)) {
                    return month;
                }
            } catch (DateTimeParseException e) {
                // 今月を表示する
            }
        }
        return months.get(0);
    }

    /** 申請画面の1か月分 */
    public RequestMonthView getMonth(long userId, YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        int daysBefore = appSettingRepository.getDeadlineDaysBefore();
        Map<LocalDate, ShiftRequest> requests = shiftRequestRepository
                .findByUserAndPeriod(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .collect(Collectors.toMap(ShiftRequest::getWorkDate, request -> request));
        Set<LocalDate> unavailable = new HashSet<>(
                cycleUnavailableRepository.findStarts(userId, month.atDay(1), month.atEndOfMonth()));

        int index = 0;
        List<RequestCycleView> cycles = new ArrayList<>();
        for (Cycle cycle : Cycle.ofMonth(month)) {
            RequestCycleView cycleView = new RequestCycleView();
            cycleView.setCycle(cycle);
            cycleView.setLabel(cycle.label());
            cycleView.setDeadlineLabel(DateLabels.monthDayWeek(cycle.deadline(daysBefore)));
            cycleView.setOpen(cycle.isOpen(today, daysBefore));
            cycleView.setUnavailable(unavailable.contains(cycle.start()));
            List<RequestDayView> days = new ArrayList<>();
            for (LocalDate date : cycle.dates()) {
                RequestDayView day = new RequestDayView();
                day.setDate(date);
                day.setLabel(DateLabels.dayWeek(date));
                if (cycleView.isOpen()) {
                    day.setIndex(index++);
                }
                ShiftRequest request = requests.get(date);
                if (request != null) {
                    day.setStartTime(TimeSlots.format(request.getStartTime()));
                    day.setEndTime(TimeSlots.format(request.getEndTime()));
                    day.setNote(request.getNote());
                }
                days.add(day);
            }
            cycleView.setDays(days);
            cycles.add(cycleView);
        }

        RequestMonthView view = new RequestMonthView();
        view.setMonth(month);
        view.setMonths(requestMonths());
        view.setCycles(cycles);
        return view;
    }

    /**
     * 1か月分の申請を一括保存する。1件でも不正があれば何も保存しない。
     * 時刻も備考も空の日は申請を削除する。送信されなかった日は変更しない。
     * 「この期間は出勤できない」は締切前のサイクルだけ反映する
     */
    @Transactional
    public void saveMonth(long userId, RequestMonthForm form) {
        YearMonth month = parseRequestMonth(form.getMonth());
        LocalDate today = LocalDate.now(clock);
        int daysBefore = appSettingRepository.getDeadlineDaysBefore();

        Map<LocalDate, RequestDayForm> inputs = parseDays(form, month);
        for (LocalDate date : inputs.keySet()) {
            checkOpen(Cycle.of(date), today, daysBefore);
        }
        Set<LocalDate> unavailableStarts = parseUnavailable(form, month, today, daysBefore);

        // 先に全件を検証し、すべて正しい場合だけ保存する
        List<ShiftRequest> saves = new ArrayList<>();
        List<LocalDate> deletes = new ArrayList<>();
        for (Map.Entry<LocalDate, RequestDayForm> entry : inputs.entrySet()) {
            LocalDate date = entry.getKey();
            RequestDayForm input = entry.getValue();
            Cycle cycle = Cycle.of(date);
            if (unavailableStarts.contains(cycle.start()) && hasInput(input)) {
                throw new BusinessException(
                        cycle.label() + "は「この期間は出勤できない」にチェックがあるため、申請を入力できません");
            }
            try {
                if (!hasInput(input)) {
                    deletes.add(date);
                } else {
                    saves.add(toRequest(userId, date, input));
                }
            } catch (BusinessException e) {
                throw new BusinessException(DateLabels.monthDay(date) + "：" + e.getMessage());
            }
        }

        for (Cycle cycle : Cycle.ofMonth(month)) {
            if (!cycle.isOpen(today, daysBefore)) {
                continue;
            }
            if (unavailableStarts.contains(cycle.start())) {
                // 出勤できない期間には申請を持たない
                cycleUnavailableRepository.insert(userId, cycle.start());
                shiftRequestRepository.deleteByUserAndPeriod(userId, cycle.start(), cycle.end());
            } else {
                cycleUnavailableRepository.delete(userId, cycle.start());
            }
        }
        deletes.forEach(date -> shiftRequestRepository.delete(userId, date));
        saves.forEach(shiftRequestRepository::upsert);
    }

    private YearMonth parseRequestMonth(String text) {
        try {
            YearMonth month = YearMonth.parse(text == null ? "" : text);
            if (requestMonths().contains(month)) {
                return month;
            }
        } catch (DateTimeParseException e) {
            // 下で入力エラーにする
        }
        throw new BusinessException("申請できない月です");
    }

    /** 日付の昇順に並べる（エラーは早い日付から報告する） */
    private Map<LocalDate, RequestDayForm> parseDays(RequestMonthForm form, YearMonth month) {
        Map<LocalDate, RequestDayForm> inputs = new TreeMap<>();
        for (RequestDayForm input : form.getDays()) {
            // 無効にした入力欄は送信されず番号が抜けるため、Springが空の要素で埋める。それは読み飛ばす
            if (input == null || (input.getDate() == null && !hasInput(input))) {
                continue;
            }
            LocalDate date = parseDate(input.getDate());
            if (!YearMonth.from(date).equals(month) || inputs.containsKey(date)) {
                throw new BusinessException(INVALID_DATE);
            }
            inputs.put(date, input);
        }
        return inputs;
    }

    /** チェックされたサイクルの開始日。対象月の締切前のサイクルの開始日だけを受け付ける */
    private Set<LocalDate> parseUnavailable(RequestMonthForm form, YearMonth month, LocalDate today,
            int daysBefore) {
        Set<LocalDate> starts = new HashSet<>();
        for (String text : form.getUnavailableCycles()) {
            LocalDate start = parseDate(text);
            Cycle cycle = Cycle.of(start);
            if (!cycle.start().equals(start) || !YearMonth.from(start).equals(month)) {
                throw new BusinessException(INVALID_DATE);
            }
            checkOpen(cycle, today, daysBefore);
            starts.add(start);
        }
        return starts;
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text);
        } catch (DateTimeParseException e) {
            throw new BusinessException(INVALID_DATE);
        }
    }

    private void checkOpen(Cycle cycle, LocalDate today, int daysBefore) {
        if (!cycle.isOpen(today, daysBefore)) {
            throw new BusinessException(cycle.label() + "は締切を過ぎたため変更できません。変更は管理者に伝えてください");
        }
    }

    /** 時刻か備考のどれかが入力されているか */
    private boolean hasInput(RequestDayForm input) {
        return !isBlank(input.getStartTime()) || !isBlank(input.getEndTime()) || !isBlank(input.getNote());
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private ShiftRequest toRequest(long userId, LocalDate date, RequestDayForm input) {
        TimeRange range = TimeRange.parse(input.getStartTime(), input.getEndTime());
        ShiftRequest request = new ShiftRequest();
        request.setUserId(userId);
        request.setWorkDate(date);
        request.setStartTime(range.start());
        request.setEndTime(range.end());
        request.setNote(RequestNote.normalize(input.getNote()));
        return request;
    }
}
