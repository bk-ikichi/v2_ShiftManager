package jp.bk.shiftmanager.mapper;

import java.util.List;
import jp.bk.shiftmanager.entity.Position;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PositionMapper {

    @Select("SELECT * FROM positions ORDER BY display_order, id")
    List<Position> findAll();

    @Select("SELECT * FROM positions WHERE hidden = FALSE ORDER BY display_order, id")
    List<Position> findVisible();

    @Select("SELECT * FROM positions WHERE id = #{id}")
    Position findById(@Param("id") long id);

    @Insert("INSERT INTO positions (name, display_order, hidden, color) "
            + "VALUES (#{name}, #{displayOrder}, #{hidden}, #{color})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(Position position);

    @Update("UPDATE positions SET name = #{name}, display_order = #{displayOrder}, hidden = #{hidden}, "
            + "color = #{color} WHERE id = #{id}")
    int update(Position position);

    @Delete("DELETE FROM positions WHERE id = #{id}")
    int delete(@Param("id") long id);

    /** スタッフの初期ポジションまたはシフトの枠として使われている件数 */
    @Select("""
            SELECT (SELECT COUNT(*) FROM users WHERE position_id = #{id})
                 + (SELECT COUNT(*) FROM shifts WHERE position_id = #{id})
            """)
    long countUsage(@Param("id") long id);

    /** 同名のポジション数（excludeIdは更新時の自分自身を除外するため。新規時は0） */
    @Select("SELECT COUNT(*) FROM positions WHERE name = #{name} AND id <> #{excludeId}")
    long countByName(@Param("name") String name, @Param("excludeId") long excludeId);
}
