package jp.bk.shiftmanager.service;

import jp.bk.shiftmanager.dto.ChangeNotice;
import jp.bk.shiftmanager.dto.HomeView;
import jp.bk.shiftmanager.dto.ShiftChangeRow;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.repository.ShiftChangeRepository;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** スタッフのトップ画面 */
@Service
@RequiredArgsConstructor
public class HomeService {

    private final ShiftChangeRepository shiftChangeRepository;

    public HomeView getHome(long userId) {
        HomeView view = new HomeView();
        view.setChanges(shiftChangeRepository.findUnacknowledgedByUser(userId).stream()
                .map(this::toNotice)
                .toList());
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
