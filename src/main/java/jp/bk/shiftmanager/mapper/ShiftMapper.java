package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
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
}
