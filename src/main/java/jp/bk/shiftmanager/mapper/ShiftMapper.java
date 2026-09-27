package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.entity.Shift;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 確定シフト */
@Mapper
public interface ShiftMapper {

    /** その日のシフト（INの早い順） */
    @Select("SELECT * FROM shifts WHERE work_date = #{date} ORDER BY start_time, end_time, id")
    List<Shift> findByDate(@Param("date") LocalDate date);
}
