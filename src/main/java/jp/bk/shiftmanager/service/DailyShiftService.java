package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.DailyShiftGroup;
import jp.bk.shiftmanager.dto.DailyShiftRow;
import jp.bk.shiftmanager.dto.DailyShiftView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.repository.PositionRepository;
import jp.bk.shiftmanager.repository.PublishedDateRepository;
import jp.bk.shiftmanager.repository.ShiftRepository;
import jp.bk.shiftmanager.repository.UserRepository;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.TimeSlots;
import jp.bk.shiftmanager.util.ViewRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 公開済みシフトの日別一覧 */
@Service
@RequiredArgsConstructor
public class DailyShiftService {

    private final Clock clock;
    private final ShiftRepository shiftRepository;
    private final PublishedDateRepository publishedDateRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;

    /** 画面から指定された日。指定がない・不正なら今日 */
    public LocalDate resolveDate(String text) {
        if (text != null) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                // 今日を表示する
            }
        }
        return LocalDate.now(clock);
    }

    /**
     * 1日分の一覧。公開済みの日だけ全員分を表示する。
     * スタッフは前月1日より前の日を閲覧できない（管理者は閲覧できる）
     */
    public DailyShiftView getDay(LocalDate date, long viewerId, boolean admin) {
        LocalDate oldest = ViewRange.staffOldest(LocalDate.now(clock));
        DailyShiftView view = new DailyShiftView();
        view.setDate(date);
        view.setDateLabel(DateLabels.monthDayWeek(date));
        view.setPreviousDate(date.minusDays(1));
        view.setPreviousLabel(DateLabels.monthDayWeek(date.minusDays(1)));
        view.setPreviousVisible(admin || !date.minusDays(1).isBefore(oldest));
        view.setNextDate(date.plusDays(1));
        view.setNextLabel(DateLabels.monthDayWeek(date.plusDays(1)));
        view.setAdmin(admin);

        if (!admin && date.isBefore(oldest)) {
            view.setMessage("先月より前のシフトは表示できません");
            return view;
        }
        if (!publishedDateRepository.isPublished(date)) {
            view.setMessage("この日のシフトはまだ公開されていません");
            return view;
        }

        // 公開後に無効化されたスタッフのシフトも表示するため、無効を含む全員から名前を引く
        Map<Long, String> names = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        // INの早い順
        List<Shift> shifts = shiftRepository.findByDate(date);
        // 非表示にしたポジションのシフトも表示するため、非表示を含む全ポジションで分ける
        for (Position position : positionRepository.findAll()) {
            List<DailyShiftRow> rows = shifts.stream()
                    .filter(shift -> shift.getPositionId().equals(position.getId()))
                    .map(shift -> toRow(shift, names, viewerId))
                    .toList();
            if (!rows.isEmpty()) {
                DailyShiftGroup group = new DailyShiftGroup();
                group.setPositionName(position.getName());
                group.setRows(rows);
                view.getGroups().add(group);
            }
        }
        if (view.getGroups().isEmpty()) {
            view.setMessage("この日の出勤者はいません");
        }
        return view;
    }

    private DailyShiftRow toRow(Shift shift, Map<Long, String> names, long viewerId) {
        DailyShiftRow row = new DailyShiftRow();
        row.setName(names.get(shift.getUserId()));
        row.setTimeLabel(TimeSlots.formatRange(shift.getStartTime(), shift.getEndTime()));
        row.setMine(shift.getUserId() == viewerId);
        return row;
    }
}
