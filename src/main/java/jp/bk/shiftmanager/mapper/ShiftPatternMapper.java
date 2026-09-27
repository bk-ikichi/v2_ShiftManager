package jp.bk.shiftmanager.mapper;

import java.util.List;
import jp.bk.shiftmanager.entity.ShiftPattern;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShiftPatternMapper {

    @Select("SELECT * FROM shift_patterns WHERE user_id = #{userId} ORDER BY start_time, end_time, id")
    List<ShiftPattern> findByUserId(@Param("userId") long userId);

    @Select("SELECT * FROM shift_patterns WHERE id = #{id}")
    ShiftPattern findById(@Param("id") long id);

    @Insert("""
            INSERT INTO shift_patterns (user_id, name, start_time, end_time)
            VALUES (#{userId}, #{name}, #{startTime}, #{endTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(ShiftPattern pattern);

    @Update("UPDATE shift_patterns SET name = #{name}, start_time = #{startTime}, end_time = #{endTime} WHERE id = #{id}")
    int update(ShiftPattern pattern);

    @Delete("DELETE FROM shift_patterns WHERE id = #{id}")
    int delete(@Param("id") long id);
}
