package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.mapper.ShiftRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ShiftRequestRepository {

    private final ShiftRequestMapper shiftRequestMapper;

    public List<ShiftRequest> findByUserAndPeriod(long userId, LocalDate from, LocalDate to) {
        return shiftRequestMapper.findByUserAndPeriod(userId, from, to);
    }

    public Optional<ShiftRequest> find(long userId, LocalDate date) {
        return Optional.ofNullable(shiftRequestMapper.find(userId, date));
    }

    public List<ShiftRequest> findByPeriod(LocalDate from, LocalDate to) {
        return shiftRequestMapper.findByPeriod(from, to);
    }

    public void upsert(ShiftRequest request) {
        shiftRequestMapper.upsert(request);
    }

    public void delete(long userId, LocalDate date) {
        shiftRequestMapper.delete(userId, date);
    }

    public void deleteByUserAndPeriod(long userId, LocalDate from, LocalDate to) {
        shiftRequestMapper.deleteByUserAndPeriod(userId, from, to);
    }
}
