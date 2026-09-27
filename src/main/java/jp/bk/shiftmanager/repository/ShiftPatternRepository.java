package jp.bk.shiftmanager.repository;

import java.util.List;
import java.util.Optional;
import jp.bk.shiftmanager.entity.ShiftPattern;
import jp.bk.shiftmanager.mapper.ShiftPatternMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ShiftPatternRepository {

    private final ShiftPatternMapper shiftPatternMapper;

    public List<ShiftPattern> findByUserId(long userId) {
        return shiftPatternMapper.findByUserId(userId);
    }

    public Optional<ShiftPattern> findById(long id) {
        return Optional.ofNullable(shiftPatternMapper.findById(id));
    }

    public void insert(ShiftPattern pattern) {
        shiftPatternMapper.insert(pattern);
    }

    public void update(ShiftPattern pattern) {
        shiftPatternMapper.update(pattern);
    }

    public void delete(long id) {
        shiftPatternMapper.delete(id);
    }
}
