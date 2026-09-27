package jp.bk.shiftmanager.service;

import java.util.List;
import jp.bk.shiftmanager.entity.ShiftPattern;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.PatternForm;
import jp.bk.shiftmanager.repository.ShiftPatternRepository;
import jp.bk.shiftmanager.util.TimeRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatternService {

    private final ShiftPatternRepository shiftPatternRepository;

    /** 本人のパターン（IN・OUTの早い順） */
    public List<ShiftPattern> findMine(long userId) {
        return shiftPatternRepository.findByUserId(userId);
    }

    @Transactional
    public void create(long userId, PatternForm form) {
        TimeRange range = TimeRange.parse(form.getStartTime(), form.getEndTime());
        ShiftPattern pattern = new ShiftPattern();
        pattern.setUserId(userId);
        apply(pattern, form, range);
        shiftPatternRepository.insert(pattern);
    }

    @Transactional
    public void update(long userId, long id, PatternForm form) {
        ShiftPattern pattern = findOwn(userId, id);
        TimeRange range = TimeRange.parse(form.getStartTime(), form.getEndTime());
        apply(pattern, form, range);
        shiftPatternRepository.update(pattern);
    }

    @Transactional
    public void delete(long userId, long id) {
        findOwn(userId, id);
        shiftPatternRepository.delete(id);
    }

    /** 本人のパターンを返す。他人のパターンは存在しないものとして扱う */
    private ShiftPattern findOwn(long userId, long id) {
        return shiftPatternRepository.findById(id)
                .filter(pattern -> pattern.getUserId() == userId)
                .orElseThrow(() -> new BusinessException("パターンが見つかりません"));
    }

    private void apply(ShiftPattern pattern, PatternForm form, TimeRange range) {
        pattern.setName(form.getName());
        pattern.setStartTime(range.start());
        pattern.setEndTime(range.end());
    }
}
