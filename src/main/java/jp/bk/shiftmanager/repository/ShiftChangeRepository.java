package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.dto.ShiftChangeRow;
import jp.bk.shiftmanager.entity.ShiftChange;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.mapper.ShiftChangeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ShiftChangeRepository {

    private final ShiftChangeMapper shiftChangeMapper;

    /** 未確認の変更を新しい種別で置き換える（未確認は1人1日1件。確認済みの記録は残す） */
    public void replaceUnacknowledged(long userId, LocalDate date, ShiftChangeType type) {
        shiftChangeMapper.deleteUnacknowledged(userId, date);
        shiftChangeMapper.insert(userId, date, type);
    }

    public List<ShiftChange> findByDate(LocalDate date) {
        return shiftChangeMapper.findByDate(date);
    }

    /** 本人の未確認の変更（日付順）。その日の現在のシフトを付ける */
    public List<ShiftChangeRow> findUnacknowledgedByUser(long userId) {
        return shiftChangeMapper.findUnacknowledgedByUser(userId);
    }

    /** 本人の未確認の変更を確認済みにする */
    public void acknowledge(long userId, long id) {
        shiftChangeMapper.acknowledge(userId, id);
    }
}
