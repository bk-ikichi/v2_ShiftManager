package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.dto.MyShiftRow;
import jp.bk.shiftmanager.entity.Shift;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 確定シフト */
@Mapper
public interface ShiftMapper {

    /** その日のシフト（INの早い順） */
    @Select("SELECT * FROM shifts WHERE work_date = #{date} ORDER BY start_time, end_time, id")
    List<Shift> findByDate(@Param("date") LocalDate date);

    @Insert("""
            INSERT INTO shifts (work_date, user_id, position_id, start_time, end_time)
            VALUES (#{workDate}, #{userId}, #{positionId}, #{startTime}, #{endTime})
            """)
    void insert(Shift shift);

    @Delete("DELETE FROM shifts WHERE work_date = #{date}")
    int deleteByDate(@Param("date") LocalDate date);

    /** シフトが1件以上ある日（昇順） */
    @Select("""
            SELECT DISTINCT work_date FROM shifts
            WHERE work_date BETWEEN #{from} AND #{to}
            ORDER BY work_date
            """)
    List<LocalDate> findDates(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 本人の公開済みシフト（日付順） */
    @Select("""
            SELECT s.work_date, s.start_time, s.end_time, p.name AS position_name
            FROM shifts s
            JOIN published_dates d ON d.work_date = s.work_date
            JOIN positions p ON p.id = s.position_id
            WHERE s.user_id = #{userId} AND s.work_date BETWEEN #{from} AND #{to}
            ORDER BY s.work_date
            """)
    List<MyShiftRow> findPublishedByUser(@Param("userId") long userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
