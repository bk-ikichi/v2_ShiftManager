package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.mapper.CycleUnavailableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CycleUnavailableRepository {

    private final CycleUnavailableMapper cycleUnavailableMapper;

    public List<LocalDate> findStarts(long userId, LocalDate from, LocalDate to) {
        return cycleUnavailableMapper.findStarts(userId, from, to);
    }

    public void insert(long userId, LocalDate cycleStart) {
        cycleUnavailableMapper.insert(userId, cycleStart);
    }

    /** 解除した行があればtrue */
    public boolean delete(long userId, LocalDate cycleStart) {
        return cycleUnavailableMapper.delete(userId, cycleStart) > 0;
    }
}
