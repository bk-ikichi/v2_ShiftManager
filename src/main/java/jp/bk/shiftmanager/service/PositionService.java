package jp.bk.shiftmanager.service;

import java.util.List;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.PositionForm;
import jp.bk.shiftmanager.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;

    public List<Position> findAll() {
        return positionRepository.findAll();
    }

    @Transactional
    public void create(PositionForm form) {
        checkNameUnique(form.getName(), 0);
        Position position = new Position();
        position.setName(form.getName());
        position.setDisplayOrder(form.getDisplayOrder());
        position.setHidden(form.isHidden());
        positionRepository.insert(position);
    }

    @Transactional
    public void update(long id, PositionForm form) {
        Position position = find(id);
        checkNameUnique(form.getName(), id);
        position.setName(form.getName());
        position.setDisplayOrder(form.getDisplayOrder());
        position.setHidden(form.isHidden());
        positionRepository.update(position);
    }

    @Transactional
    public void delete(long id) {
        find(id);
        if (positionRepository.isUsed(id)) {
            throw new BusinessException("使用中のため削除できません。非表示にしてください");
        }
        positionRepository.delete(id);
    }

    private Position find(long id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ポジションが見つかりません"));
    }

    private void checkNameUnique(String name, long excludeId) {
        if (positionRepository.existsName(name, excludeId)) {
            throw new BusinessException("同じ名前のポジションが既にあります");
        }
    }
}
