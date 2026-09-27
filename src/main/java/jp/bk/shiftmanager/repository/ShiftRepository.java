package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import java.util.List;
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
}
