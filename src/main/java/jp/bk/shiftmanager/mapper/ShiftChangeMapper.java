package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.entity.ShiftChange;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 公開後の変更マーク */
@Mapper
public interface ShiftChangeMapper {

    @Delete("""
            DELETE FROM shift_changes
            WHERE user_id = #{userId} AND work_date = #{date} AND acknowledged_at IS NULL
            """)
    int deleteUnacknowledged(@Param("userId") long userId, @Param("date") LocalDate date);

    @Insert("INSERT INTO shift_changes (user_id, work_date, change_type) VALUES (#{userId}, #{date}, #{type})")
    void insert(@Param("userId") long userId, @Param("date") LocalDate date, @Param("type") ShiftChangeType type);

    /** その日の変更（確認済みを含む。記録順） */
    @Select("SELECT * FROM shift_changes WHERE work_date = #{date} ORDER BY id")
    List<ShiftChange> findByDate(@Param("date") LocalDate date);
}
