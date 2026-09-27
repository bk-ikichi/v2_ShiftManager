package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.CalendarDay;
import jp.bk.shiftmanager.dto.CalendarView;
import jp.bk.shiftmanager.dto.ChangeNotice;
import jp.bk.shiftmanager.dto.DeadlineNotice;
import jp.bk.shiftmanager.dto.HomeView;
import jp.bk.shiftmanager.dto.MyShiftRow;
import jp.bk.shiftmanager.dto.MyShiftView;
import jp.bk.shiftmanager.dto.ShiftChangeRow;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import jp.bk.shiftmanager.repository.CycleUnavailableRepository;
import jp.bk.shiftmanager.repository.PublishedDateRepository;
import jp.bk.shiftmanager.repository.ShiftChangeRepository;
import jp.bk.shiftmanager.repository.ShiftRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.util.Cycle;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.TimeSlots;
import jp.bk.shiftmanager.util.ViewRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** スタッフのトップ画面 */
@Service
@RequiredArgsConstructor
public class HomeService {

    private final Clock clock;
    private final ShiftChangeRepository shiftChangeRepository;
    private final ShiftRepository shiftRepository;
    private final PublishedDateRepository publishedDateRepository;
    private final AppSettingRepository appSettingRepository;
    private final ShiftRequestRepository shiftRequestRepository;
    private final CycleUnavailableRepository cycleUnavailableRepository;

    /**
     * トップ画面。
     * @param month カレンダーに表示する月（yyyy-MM）。指定がない・不正・範囲外なら今月
     */
    public HomeView getHome(long userId, boolean admin, String month) {
        LocalDate today = LocalDate.now(clock);
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth nextMonth = thisMonth.plusMonths(1);
        // 今月1日〜来月末の公開済みシフト（日付順）。次回の出勤と予定時間に使う
        List<MyShiftRow> shifts = shiftRepository.findPublishedByUser(
                userId, thisMonth.atDay(1), nextMonth.atEndOfMonth());
        List<ShiftChangeRow> changes = shiftChangeRepository.findUnacknowledgedByUser(userId);

        HomeView view = new HomeView();
        view.setChanges(changes.stream().map(this::toNotice).toList());
        view.setNextShift(shifts.stream()
                .filter(shift -> !shift.getWorkDate().isBefore(today))
                .findFirst()
                .map(shift -> toMyShift(shift, today))
                .orElse(null));
        view.setCalendar(calendar(userId, admin, resolveMonth(month, thisMonth, admin), today, changes));
        view.setDeadline(deadline(userId, today));
        view.setThisMonthLabel(thisMonth.getMonthValue() + "月");
        view.setThisMonthHours(hoursLabel(minutes(shifts, thisMonth)));
        view.setNextMonthLabel(nextMonth.getMonthValue() + "月");
        view.setNextMonthHours(hoursLabel(minutes(shifts, nextMonth)));
        return view;
    }

    /** 本人の未確認の変更を確認済みにする。他人の変更・確認済みの変更・不正なIDなら何もしない */
    @Transactional
    public void acknowledge(long userId, String changeId) {
        long id;
        try {
            id = Long.parseLong(changeId == null ? "" : changeId.strip());
        } catch (NumberFormatException e) {
            return;
        }
        shiftChangeRepository.acknowledge(userId, id);
    }

    /**
     * 画面から指定された月。指定がない・不正・範囲外なら今月。
     * 範囲はスタッフが先月〜2か月後、管理者は過去を制限せず2か月後まで
     */
    private YearMonth resolveMonth(String text, YearMonth thisMonth, boolean admin) {
        if (text != null) {
            try {
                YearMonth month = YearMonth.parse(text);
                if (!month.isAfter(thisMonth.plusMonths(ViewRange.FUTURE_MONTHS))
                        && (admin || !month.isBefore(thisMonth.minusMonths(1)))) {
                    return month;
                }
            } catch (DateTimeParseException e) {
                // 今月を表示する
            }
        }
        return thisMonth;
    }

    /**
     * 月カレンダー（日曜始まり）。公開済みの日は日別一覧へ移動でき、本人のシフトがあればINを表示する。
     * スタッフは前月1日より前の日を移動できず、INも表示しない（管理者は表示する）
     */
    private CalendarView calendar(long userId, boolean admin, YearMonth month, LocalDate today,
            List<ShiftChangeRow> changes) {
        LocalDate oldest = ViewRange.staffOldest(today);
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        Set<LocalDate> published = new HashSet<>(publishedDateRepository.findDates(first, last));
        // 本人のシフトは1日1件（shifts_date_user_key）
        Map<LocalDate, LocalTime> starts = shiftRepository.findPublishedByUser(userId, first, last).stream()
                .collect(Collectors.toMap(MyShiftRow::getWorkDate, MyShiftRow::getStartTime));
        Set<LocalDate> changed = changes.stream().map(ShiftChangeRow::getWorkDate).collect(Collectors.toSet());

        List<List<CalendarDay>> weeks = new ArrayList<>();
        // 月初を含む週の日曜日から、月末を含む週の土曜日まで（getValue は月曜=1〜日曜=7）
        LocalDate sunday = first.minusDays(first.getDayOfWeek().getValue() % 7);
        for (LocalDate weekStart = sunday; !weekStart.isAfter(last); weekStart = weekStart.plusWeeks(1)) {
            List<CalendarDay> week = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate date = weekStart.plusDays(i);
                CalendarDay day = new CalendarDay();
                day.setDate(date);
                day.setDay(date.getDayOfMonth());
                day.setInMonth(YearMonth.from(date).equals(month));
                day.setToday(date.equals(today));
                if (day.isInMonth()) {
                    day.setLinkable(published.contains(date) && (admin || !date.isBefore(oldest)));
                    if (day.isLinkable() && starts.containsKey(date)) {
                        day.setStartLabel(TimeSlots.format(starts.get(date)));
                    }
                    day.setChanged(changed.contains(date));
                }
                week.add(day);
            }
            weeks.add(week);
        }

        CalendarView calendar = new CalendarView();
        calendar.setMonthLabel(month.getYear() + "年" + month.getMonthValue() + "月");
        calendar.setWeeks(weeks);
        calendar.setPreviousMonth(month.minusMonths(1));
        calendar.setPreviousVisible(admin || !month.minusMonths(1).atEndOfMonth().isBefore(oldest));
        calendar.setNextMonth(month.plusMonths(1));
        calendar.setNextVisible(month.isBefore(YearMonth.from(today).plusMonths(ViewRange.FUTURE_MONTHS)));
        return calendar;
    }

    /**
     * 締切前（今日が締切日以前）で最も近いサイクルの案内。
     * 提出済みの判定は申請画面と同じ（サイクル内に申請が1日以上、または「この期間は出勤できない」にチェック）
     */
    private DeadlineNotice deadline(long userId, LocalDate today) {
        int daysBefore = appSettingRepository.getDeadlineDaysBefore();
        Cycle cycle = Cycle.of(today);
        while (!cycle.isOpen(today, daysBefore)) {
            cycle = cycle.next();
        }
        boolean submitted = !shiftRequestRepository.findByUserAndPeriod(userId, cycle.start(), cycle.end()).isEmpty()
                || !cycleUnavailableRepository.findStarts(userId, cycle.start(), cycle.start()).isEmpty();

        DeadlineNotice notice = new DeadlineNotice();
        notice.setCycleLabel(cycle.label());
        notice.setDeadlineLabel(DateLabels.monthDayWeek(cycle.deadline(daysBefore)));
        notice.setSubmitted(submitted);
        notice.setMonth(YearMonth.from(cycle.start()));
        return notice;
    }

    /** その月のシフトの OUT − IN の合計（分） */
    private long minutes(List<MyShiftRow> shifts, YearMonth month) {
        return shifts.stream()
                .filter(shift -> YearMonth.from(shift.getWorkDate()).equals(month))
                .mapToLong(shift -> Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes())
                .sum();
    }

    /** 例：13時間30分、4時間、0時間 */
    private String hoursLabel(long minutes) {
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + "時間" : hours + "時間" + rest + "分";
    }

    private MyShiftView toMyShift(MyShiftRow row, LocalDate today) {
        MyShiftView view = new MyShiftView();
        view.setDate(row.getWorkDate());
        view.setDateLabel(DateLabels.monthDayWeek(row.getWorkDate()));
        view.setTimeLabel(TimeSlots.formatRange(row.getStartTime(), row.getEndTime()));
        view.setPositionName(row.getPositionName());
        view.setToday(row.getWorkDate().equals(today));
        return view;
    }

    private ChangeNotice toNotice(ShiftChangeRow row) {
        ChangeNotice notice = new ChangeNotice();
        notice.setId(row.getId());
        notice.setDate(row.getWorkDate());
        notice.setDateLabel(DateLabels.monthDayWeek(row.getWorkDate()));
        notice.setTypeLabel(typeLabel(row.getChangeType()));
        // その日の現在のシフトがなければ取り消し（取り消しの記録は必ずシフトがない状態で作られる）
        notice.setCancelled(row.getStartTime() == null);
        if (!notice.isCancelled()) {
            notice.setTimeLabel(TimeSlots.formatRange(row.getStartTime(), row.getEndTime()));
            notice.setPositionName(row.getPositionName());
        }
        return notice;
    }

    private String typeLabel(ShiftChangeType type) {
        return switch (type) {
            case ADDED -> "追加";
            case UPDATED -> "変更";
            case CANCELLED -> "取り消し";
        };
    }
}
