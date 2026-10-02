package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.DateOption;
import jp.bk.shiftmanager.dto.ShiftCandidate;
import jp.bk.shiftmanager.dto.ShiftDayView;
import jp.bk.shiftmanager.dto.ShiftGroupView;
import jp.bk.shiftmanager.dto.ShiftRequestInfo;
import jp.bk.shiftmanager.dto.ShiftRowView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.ShiftDayForm;
import jp.bk.shiftmanager.form.ShiftRowForm;
import jp.bk.shiftmanager.repository.PositionRepository;
import jp.bk.shiftmanager.repository.PublishedDateRepository;
import jp.bk.shiftmanager.repository.ShiftChangeRepository;
import jp.bk.shiftmanager.repository.ShiftRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.repository.UserRepository;
import jp.bk.shiftmanager.util.Cycle;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.ShiftChanges;
import jp.bk.shiftmanager.util.ShiftWarnings;
import jp.bk.shiftmanager.util.TimeRange;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 確定シフトの転記（管理者） */
@Service
@RequiredArgsConstructor
public class ShiftService {

    /** 未入力の日に表示する、ポジションごとの空欄行の数 */
    private static final int BLANK_ROWS = 6;
    /** 申請がない場合の申請IN・OUTの表示 */
    private static final String NO_REQUEST_TIME = "--:--";
    private static final String USER_NOT_FOUND = "スタッフが見つかりません";

    private final Clock clock;
    private final ShiftRepository shiftRepository;
    private final PublishedDateRepository publishedDateRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final ShiftRequestRepository shiftRequestRepository;
    private final ShiftChangeRepository shiftChangeRepository;

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

    /** 転記画面の表示内容。未入力の日はポジションごとに空欄行を付ける */
    public ShiftDayView getDay(LocalDate date) {
        List<Shift> shifts = shiftRepository.findByDate(date);
        Map<Long, List<ShiftRowForm>> rows = new HashMap<>();
        for (Shift shift : shifts) {
            rows.computeIfAbsent(shift.getPositionId(), id -> new ArrayList<>()).add(toRowForm(shift));
        }
        return buildView(date, shifts, rows, shifts.isEmpty());
    }

    /** 登録できなかった入力をそのまま表示し直す（空欄行も送信されたまま残し、付け足さない） */
    public ShiftDayView getDay(LocalDate date, ShiftDayForm input) {
        Map<Long, List<ShiftRowForm>> rows = new HashMap<>();
        for (ShiftRowForm row : input.getRows()) {
            Long positionId = row == null ? null : parseId(row.getPositionId());
            if (positionId != null) {
                rows.computeIfAbsent(positionId, id -> new ArrayList<>()).add(row);
            }
        }
        return buildView(date, shiftRepository.findByDate(date), rows, false);
    }

    /**
     * 1日分のシフトを登録し直す。空欄の行は捨て、1行でも不正があれば何も保存しない。
     * 並び順は保存せず、表示時にポジションの表示順 → INの早い順に並べる。
     * 公開済みの日は、登録前後の差分を「変更あり」として記録する
     */
    @Transactional
    public void saveDay(ShiftDayForm form) {
        LocalDate date = parseDate(form.getDate());
        boolean published = publishedDateRepository.isPublished(date);
        // 下書きとして開いた画面から、確認なしにスタッフへ反映させない
        if (published && !form.isPublished()) {
            throw new BusinessException(
                    "この日は画面を開いた後に公開されました。変更はすぐスタッフに表示されるため、内容を確認してもう一度登録してください");
        }
        List<Shift> before = shiftRepository.findByDate(date);
        List<Shift> after = parseRows(date, form, before);
        shiftRepository.deleteByDate(date);
        after.forEach(shiftRepository::insert);
        if (published) {
            recordChanges(date, before, after);
        }
    }

    /** 公開済みの日の変更を記録する。差分のないスタッフは記録しない */
    private void recordChanges(LocalDate date, List<Shift> before, List<Shift> after) {
        Map<Long, Shift> beforeByUser = before.stream()
                .collect(Collectors.toMap(Shift::getUserId, Function.identity()));
        Map<Long, Shift> afterByUser = after.stream()
                .collect(Collectors.toMap(Shift::getUserId, Function.identity()));
        Set<Long> userIds = new TreeSet<>(beforeByUser.keySet());
        userIds.addAll(afterByUser.keySet());
        for (Long userId : userIds) {
            ShiftChanges.detect(beforeByUser.get(userId), afterByUser.get(userId))
                    .ifPresent(type -> shiftChangeRepository.replaceUnacknowledged(userId, date, type));
        }
    }

    /** 全行を検証してシフトにする。エラーはどのポジション・誰の行か分かるメッセージにする */
    private List<Shift> parseRows(LocalDate date, ShiftDayForm form, List<Shift> before) {
        Map<Long, Position> positions = positionRepository.findAll().stream()
                .collect(Collectors.toMap(Position::getId, position -> position));
        Map<Long, User> users = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Set<Long> assigned = before.stream().map(Shift::getUserId).collect(Collectors.toSet());
        Set<Long> chosen = new HashSet<>();

        List<Shift> shifts = new ArrayList<>();
        for (ShiftRowForm row : form.getRows()) {
            if (row == null || isBlankRow(row)) {
                continue;
            }
            Position position = positions.get(parseId(row.getPositionId()));
            if (position == null) {
                throw new BusinessException("不正なポジションです");
            }
            if (isBlank(row.getUserId())) {
                throw new BusinessException(position.getName() + "：名前を選択してください");
            }
            User user = users.get(parseId(row.getUserId()));
            if (user == null) {
                throw new BusinessException(USER_NOT_FOUND);
            }
            String label = position.getName() + "・" + user.getName();
            // 無効化したスタッフは、その日に登録済みの場合だけ残せる
            if (!user.isEnabled() && !assigned.contains(user.getId())) {
                throw new BusinessException(label + "：無効なスタッフは選択できません");
            }
            if (!chosen.add(user.getId())) {
                throw new BusinessException(user.getName() + "が2回選ばれています。1日に1回だけ選択してください");
            }
            TimeRange range;
            try {
                range = TimeRange.parse(row.getStartTime(), row.getEndTime());
            } catch (BusinessException e) {
                throw new BusinessException(label + "：" + e.getMessage());
            }
            Shift shift = new Shift();
            shift.setWorkDate(date);
            shift.setUserId(user.getId());
            shift.setPositionId(position.getId());
            shift.setStartTime(range.start());
            shift.setEndTime(range.end());
            shifts.add(shift);
        }
        return shifts;
    }

    /** 名前・IN・OUTがすべて空の行（ポジションは雛形に常に入っているため見ない） */
    private boolean isBlankRow(ShiftRowForm row) {
        return isBlank(row.getUserId()) && isBlank(row.getStartTime()) && isBlank(row.getEndTime());
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text);
        } catch (DateTimeParseException e) {
            throw new BusinessException("不正な日付です");
        }
    }

    /**
     * 画面の内容を組み立てる。
     * shifts は登録済みのシフト（無効なスタッフを候補に残すかの判定に使う）、rowsByPosition はポジションごとの表示する行
     */
    private ShiftDayView buildView(LocalDate date, List<Shift> shifts, Map<Long, List<ShiftRowForm>> rowsByPosition,
            boolean addBlankRows) {
        List<Position> positions = positionRepository.findAll();
        List<User> users = userRepository.findAll();
        Map<Long, User> usersById = users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<User> candidates = candidates(positions, users, shifts);
        Map<String, ShiftRequest> requests = shiftRequestRepository.findByPeriod(date, date).stream()
                .collect(Collectors.toMap(request -> request.getUserId().toString(), request -> request));

        int index = 0;
        List<ShiftGroupView> groups = new ArrayList<>();
        for (Position position : positions) {
            List<ShiftRowForm> inputs = rowsByPosition.getOrDefault(position.getId(), List.of());
            // 非表示のポジションは、その日に使われている場合だけ表示する
            if (position.isHidden() && inputs.isEmpty()) {
                continue;
            }
            List<ShiftRowView> rows = new ArrayList<>();
            for (ShiftRowForm input : inputs) {
                rows.add(toRowView(index++, input, requests));
            }
            if (addBlankRows) {
                for (int i = 0; i < BLANK_ROWS; i++) {
                    rows.add(toRowView(index++, new ShiftRowForm(), requests));
                }
            }
            ShiftGroupView group = new ShiftGroupView();
            group.setPositionId(position.getId());
            group.setPositionName(position.getName());
            group.setPrimaryCandidates(candidates.stream()
                    .filter(user -> position.getId().equals(user.getPositionId()))
                    .map(this::toCandidate).toList());
            group.setOtherCandidates(candidates.stream()
                    .filter(user -> !position.getId().equals(user.getPositionId()))
                    .map(this::toCandidate).toList());
            group.setRows(rows);
            groups.add(group);
        }

        ShiftDayView view = new ShiftDayView();
        view.setDate(date);
        view.setDateLabel(DateLabels.monthDayWeek(date));
        view.setPreviousDate(date.minusDays(1));
        view.setPreviousLabel(DateLabels.monthDayWeek(date.minusDays(1)));
        view.setNextDate(date.plusDays(1));
        view.setNextLabel(DateLabels.monthDayWeek(date.plusDays(1)));
        view.setPublished(publishedDateRepository.isPublished(date));
        view.setGroups(groups);
        // 「希望シフトを反映する」で上から順に入るよう、INの早い順 → 名前の順にする
        view.setRequests(requests.values().stream()
                .filter(request -> usersById.containsKey(request.getUserId()))
                .sorted(Comparator.comparing(ShiftRequest::getStartTime)
                        .thenComparing(request -> usersById.get(request.getUserId()).getName()))
                .map(request -> toRequestInfo(request, usersById.get(request.getUserId())))
                .toList());
        view.setNextIndex(index);
        Cycle cycle = Cycle.of(date);
        view.setRangeStart(cycle.start());
        view.setRangeEnd(cycle.end());
        view.setRangeOptions(cycle.previous().start().datesUntil(cycle.next().end().plusDays(1))
                .map(option -> new DateOption(option, DateLabels.monthDayWeek(option)))
                .toList());
        return view;
    }

    /**
     * 名前の候補：有効なスタッフと、その日に登録済みのスタッフ（無効化されていても残す）。
     * 初期ポジションの表示順（未設定は最後）→ 名前の順
     */
    private List<User> candidates(List<Position> positions, List<User> users, List<Shift> shifts) {
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < positions.size(); i++) {
            order.put(positions.get(i).getId(), i);
        }
        Set<Long> assigned = shifts.stream().map(Shift::getUserId).collect(Collectors.toSet());
        // findAll は名前の順のため、安定ソートでポジションの表示順に並べ替える
        return users.stream()
                .filter(user -> user.isEnabled() || assigned.contains(user.getId()))
                .sorted(Comparator.comparingInt(
                        (User user) -> order.getOrDefault(user.getPositionId(), Integer.MAX_VALUE)))
                .toList();
    }

    private ShiftRowView toRowView(int index, ShiftRowForm input, Map<String, ShiftRequest> requests) {
        ShiftRowView row = new ShiftRowView();
        row.setIndex(index);
        row.setUserId(input.getUserId());
        row.setStartTime(input.getStartTime());
        row.setEndTime(input.getEndTime());
        if (isBlank(input.getUserId())) {
            row.setRequestStart("");
            row.setRequestEnd("");
            return row;
        }
        ShiftRequest request = requests.get(input.getUserId().strip());
        row.setRequestStart(request == null ? NO_REQUEST_TIME : TimeSlots.format(request.getStartTime()));
        row.setRequestEnd(request == null ? NO_REQUEST_TIME : TimeSlots.format(request.getEndTime()));
        row.setRequestNote(request == null ? null : request.getNote());
        TimeRange requested = request == null ? null : new TimeRange(request.getStartTime(), request.getEndTime());
        row.setWarning(ShiftWarnings.of(parseTimeOrNull(input.getStartTime()), parseTimeOrNull(input.getEndTime()),
                requested));
        return row;
    }

    /** 警告の判定用。不正な時刻は未選択として扱う（登録時に入力エラーになる） */
    private LocalTime parseTimeOrNull(String text) {
        try {
            return TimeSlots.parse(text);
        } catch (BusinessException e) {
            return null;
        }
    }

    private ShiftRowForm toRowForm(Shift shift) {
        ShiftRowForm row = new ShiftRowForm();
        row.setPositionId(shift.getPositionId().toString());
        row.setUserId(shift.getUserId().toString());
        row.setStartTime(TimeSlots.format(shift.getStartTime()));
        row.setEndTime(TimeSlots.format(shift.getEndTime()));
        return row;
    }

    private ShiftCandidate toCandidate(User user) {
        return new ShiftCandidate(user.getId().toString(), user.getName());
    }

    private ShiftRequestInfo toRequestInfo(ShiftRequest request, User user) {
        return new ShiftRequestInfo(request.getUserId().toString(), user.getName(),
                user.getPositionId() == null ? null : user.getPositionId().toString(),
                TimeSlots.format(request.getStartTime()), TimeSlots.format(request.getEndTime()), request.getNote());
    }

    /** 数値でなければnull */
    private Long parseId(String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return Long.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
