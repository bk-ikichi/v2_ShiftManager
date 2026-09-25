package jp.bk.shiftmanager.repository;

import java.util.List;
import java.util.Optional;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.mapper.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PositionRepository {

    private final PositionMapper positionMapper;

    public List<Position> findAll() {
        return positionMapper.findAll();
    }

    public List<Position> findVisible() {
        return positionMapper.findVisible();
    }

    public Optional<Position> findById(long id) {
        return Optional.ofNullable(positionMapper.findById(id));
    }

    public void insert(Position position) {
        positionMapper.insert(position);
    }

    public void update(Position position) {
        positionMapper.update(position);
    }

    public void delete(long id) {
        positionMapper.delete(id);
    }

    /** スタッフの初期ポジションまたはシフトの枠として使われているか */
    public boolean isUsed(long id) {
        return positionMapper.countUsage(id) > 0;
    }

    public boolean existsName(String name, long excludeId) {
        return positionMapper.countByName(name, excludeId) > 0;
    }
}
