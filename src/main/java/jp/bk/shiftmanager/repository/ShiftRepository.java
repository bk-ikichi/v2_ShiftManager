package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.dto.MyShiftRow;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.mapper.ShiftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ShiftRepository {

    private final ShiftMapper shiftMapper;

    /** その日のシフト（INの早い順） */
    public List<Shift> findByDate(LocalDate date) {
        return shiftMapper.findByDate(date);
    }

    public void insert(Shift shift) {
        shiftMapper.insert(shift);
    }

    public void deleteByDate(LocalDate date) {
        shiftMapper.deleteByDate(date);
    }

    /** シフトが1件以上ある日（昇順） */
    public List<LocalDate> findDates(LocalDate from, LocalDate to) {
        return shiftMapper.findDates(from, to);
    }

    /** 本人の公開済みシフト（日付順） */
    public List<MyShiftRow> findPublishedByUser(long userId, LocalDate from, LocalDate to) {
        return shiftMapper.findPublishedByUser(userId, from, to);
    }
}
