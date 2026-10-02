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
import jp.bk.shiftmanager.dto.RequestEditView;
import jp.bk.shiftmanager.dto.RequestTableRow;
import jp.bk.shiftmanager.dto.RequestTableView;
import jp.bk.shiftmanager.dto.StaffRow;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.RequestEditForm;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import jp.bk.shiftmanager.repository.CycleUnavailableRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.repository.UserRepository;
import jp.bk.shiftmanager.util.Cycle;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.RequestNote;
import jp.bk.shiftmanager.util.TimeRange;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理者による申請の閲覧・代理編集 */
@Service
@RequiredArgsConstructor
public class AdminRequestService {

    private static final String USER_NOT_FOUND = "スタッフが見つかりません";

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

    /** スタッフの並び順を、渡された順番（先頭が1）で保存する */
    @Transactional
    public void saveOrder(List<Long> userIds) {
        if (userIds.isEmpty() || new HashSet<>(userIds).size() != userIds.size()) {
            throw new BusinessException("並び順が正しくありません");
        }
        for (int i = 0; i < userIds.size(); i++) {
            long userId = userIds.get(i);
            if (userRepository.findById(userId).isEmpty()) {
                throw new BusinessException(USER_NOT_FOUND);
            }
            userRepository.updateDisplayOrder(userId, i + 1);
        }
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
        view.setDates(dates);
        view.setDateLabels(dates.stream().map(DateLabels::dayWeek).toList());
        view.setRows(rows);
        return view;
    }

    /** 代理編集画面の表示内容 */
    public RequestEditView getForEdit(long userId, LocalDate date) {
        User user = findUser(userId);
        RequestEditView view = new RequestEditView();
        view.setUserId(userId);
        view.setUserName(user.getName());
        view.setDate(date);
        view.setDateLabel(DateLabels.monthDayWeek(date));
        shiftRequestRepository.find(userId, date).ifPresent(request -> {
            view.setExists(true);
            view.setStartTime(TimeSlots.format(request.getStartTime()));
            view.setEndTime(TimeSlots.format(request.getEndTime()));
            view.setNote(request.getNote());
        });
        return view;
    }

    /**
     * 管理者による代理登録（締切後も可）。
     * 「この期間は出勤できない」にしていたサイクルならチェックを外し、外した場合はtrueを返す
     */
    @Transactional
    public boolean save(RequestEditForm form) {
        if (form.getUserId() == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        findUser(form.getUserId());
        LocalDate date = parseDate(form.getDate());
        TimeRange range = TimeRange.parse(form.getStartTime(), form.getEndTime());

        ShiftRequest request = new ShiftRequest();
        request.setUserId(form.getUserId());
        request.setWorkDate(date);
        request.setStartTime(range.start());
        request.setEndTime(range.end());
        request.setNote(RequestNote.normalize(form.getNote()));
        shiftRequestRepository.upsert(request);
        // 出勤できない期間には申請を持たない
        return cycleUnavailableRepository.delete(form.getUserId(), Cycle.of(date).start());
    }

    @Transactional
    public void delete(long userId, LocalDate date) {
        findUser(userId);
        shiftRequestRepository.delete(userId, date);
    }

    private User findUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text);
        } catch (DateTimeParseException e) {
            throw new BusinessException("不正な日付です");
        }
    }

    private RequestCell toCell(LocalDate date, ShiftRequest request) {
        if (request == null) {
            return new RequestCell(date, null, null, null);
        }
        return new RequestCell(date, TimeSlots.formatCompact(request.getStartTime()),
                TimeSlots.formatCompact(request.getEndTime()), request.getNote());
    }
}
