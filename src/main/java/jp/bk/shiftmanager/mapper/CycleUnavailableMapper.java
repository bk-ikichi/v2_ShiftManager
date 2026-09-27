package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 「この期間は出勤できない」チェック */
@Mapper
public interface CycleUnavailableMapper {

    /** 開始日が期間内にある「出勤できない」サイクルの開始日 */
    @Select("""
            SELECT cycle_start FROM cycle_unavailable
            WHERE user_id = #{userId} AND cycle_start BETWEEN #{from} AND #{to}
            ORDER BY cycle_start
            """)
    List<LocalDate> findStarts(@Param("userId") long userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Insert("""
            INSERT INTO cycle_unavailable (user_id, cycle_start) VALUES (#{userId}, #{cycleStart})
            ON CONFLICT DO NOTHING
            """)
    void insert(@Param("userId") long userId, @Param("cycleStart") LocalDate cycleStart);

    @Delete("DELETE FROM cycle_unavailable WHERE user_id = #{userId} AND cycle_start = #{cycleStart}")
    int delete(@Param("userId") long userId, @Param("cycleStart") LocalDate cycleStart);
}
