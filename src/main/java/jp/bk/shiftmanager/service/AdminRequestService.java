package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.RequestCell;
import jp.bk.shiftmanager.dto.RequestTableRow;
import jp.bk.shiftmanager.dto.RequestTableView;
import jp.bk.shiftmanager.dto.StaffRow;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import jp.bk.shiftmanager.repository.CycleUnavailableRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.repository.UserRepository;
import jp.bk.shiftmanager.util.Cycle;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 管理者による申請の閲覧・代理編集 */
@Service
@RequiredArgsConstructor
public class AdminRequestService {

    private final Clock clock;
    private final AppSettingRepository appSettingRepository;
    private final UserRepository userRepository;
    private final ShiftRequestRepository shiftRequestRepository;
    private final CycleUnavailableRepository cycleUnavailableRepository;

    /** 指定日を含むサイクル。指定がない・不正なら今日の次のサイクル */
    public Cycle resolveCycle(String date) {
        if (date != null) {
            try {
                return Cycle.of(LocalDate.parse(date));
            } catch (DateTimeParseException e) {
                // 次のサイクルを表示する
            }
        }
        return Cycle.of(LocalDate.now(clock)).next();
    }

    /** サイクル内の有効なスタッフ全員の申請表 */
    public RequestTableView getTable(Cycle cycle) {
        Map<Long, Map<LocalDate, ShiftRequest>> requests = shiftRequestRepository
                .findByPeriod(cycle.start(), cycle.end()).stream()
                .collect(Collectors.groupingBy(ShiftRequest::getUserId,
                        Collectors.toMap(ShiftRequest::getWorkDate, request -> request)));
        Set<Long> unavailable = new HashSet<>(cycleUnavailableRepository.findUserIds(cycle.start()));
        List<LocalDate> dates = cycle.dates();

        List<RequestTableRow> rows = new ArrayList<>();
        for (StaffRow staff : userRepository.findStaffRows()) {
            if (!staff.isEnabled()) {
                continue;
            }
            Map<LocalDate, ShiftRequest> byDate = requests.getOrDefault(staff.getId(), Map.of());
            RequestTableRow row = new RequestTableRow();
            row.setUserId(staff.getId());
            row.setName(staff.getName());
            row.setPositionName(staff.getPositionName());
            row.setUnavailable(unavailable.contains(staff.getId()));
            row.setSubmitted(row.isUnavailable() || !byDate.isEmpty());
            row.setCells(dates.stream().map(date -> toCell(date, byDate.get(date))).toList());
            rows.add(row);
        }

        RequestTableView view = new RequestTableView();
        view.setCycle(cycle);
        view.setLabel(cycle.label());
        view.setDeadlineLabel(DateLabels.monthDayWeek(cycle.deadline(appSettingRepository.getDeadlineDaysBefore())));
        view.setPreviousStart(cycle.previous().start());
        view.setNextStart(cycle.next().start());
        view.setDateLabels(dates.stream().map(DateLabels::dayWeek).toList());
        view.setRows(rows);
        return view;
    }

    private RequestCell toCell(LocalDate date, ShiftRequest request) {
        if (request == null) {
            return new RequestCell(date, null, null, null);
        }
        return new RequestCell(date, TimeSlots.format(request.getStartTime()),
                TimeSlots.format(request.getEndTime()), request.getNote());
    }
}
