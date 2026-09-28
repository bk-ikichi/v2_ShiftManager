package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 公開済みの日付 */
@Mapper
public interface PublishedDateMapper {

    @Select("SELECT EXISTS (SELECT 1 FROM published_dates WHERE work_date = #{date})")
    boolean exists(@Param("date") LocalDate date);

    /** 公開済みなら何もしない */
    @Insert("INSERT INTO published_dates (work_date) VALUES (#{date}) ON CONFLICT DO NOTHING")
    void insert(@Param("date") LocalDate date);

    /** 公開済みの最後の日。1日もなければnull */
    @Select("SELECT MAX(work_date) FROM published_dates")
    LocalDate findLatest();

    /** 期間内の公開済みの日（日付順） */
    @Select("SELECT work_date FROM published_dates WHERE work_date BETWEEN #{from} AND #{to} ORDER BY work_date")
    List<LocalDate> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
