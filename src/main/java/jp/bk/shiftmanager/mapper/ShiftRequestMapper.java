package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.entity.ShiftRequest;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShiftRequestMapper {

    @Select("""
            SELECT * FROM shift_requests
            WHERE user_id = #{userId} AND work_date BETWEEN #{from} AND #{to}
            ORDER BY work_date
            """)
    List<ShiftRequest> findByUserAndPeriod(@Param("userId") long userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** 同じ日の申請があれば上書きする */
    @Insert("""
            INSERT INTO shift_requests (user_id, work_date, start_time, end_time, note)
            VALUES (#{userId}, #{workDate}, #{startTime}, #{endTime}, #{note})
            ON CONFLICT (user_id, work_date) DO UPDATE
            SET start_time = EXCLUDED.start_time, end_time = EXCLUDED.end_time,
                note = EXCLUDED.note, updated_at = now()
            """)
    void upsert(ShiftRequest request);

    @Delete("DELETE FROM shift_requests WHERE user_id = #{userId} AND work_date = #{date}")
    int delete(@Param("userId") long userId, @Param("date") LocalDate date);
}
