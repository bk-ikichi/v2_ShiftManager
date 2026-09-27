package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.dto.ShiftChangeRow;
import jp.bk.shiftmanager.entity.ShiftChange;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /** 本人の未確認の変更（日付順）。その日の現在のシフトを付ける */
    @Select("""
            SELECT c.id, c.work_date, c.change_type, s.start_time, s.end_time, p.name AS position_name
            FROM shift_changes c
            LEFT JOIN shifts s ON s.user_id = c.user_id AND s.work_date = c.work_date
            LEFT JOIN positions p ON p.id = s.position_id
            WHERE c.user_id = #{userId} AND c.acknowledged_at IS NULL
            ORDER BY c.work_date, c.id
            """)
    List<ShiftChangeRow> findUnacknowledgedByUser(@Param("userId") long userId);

    /** 本人の未確認の変更を確認済みにする（他人の変更・確認済みの変更は更新しない） */
    @Update("""
            UPDATE shift_changes SET acknowledged_at = now()
            WHERE id = #{id} AND user_id = #{userId} AND acknowledged_at IS NULL
            """)
    int acknowledge(@Param("userId") long userId, @Param("id") long id);
}
