package jp.bk.shiftmanager.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppSettingMapper {

    /** 締切＝サイクル開始日の何日前か */
    @Select("SELECT deadline_days_before FROM app_settings WHERE id = 1")
    int getDeadlineDaysBefore();

    @Update("UPDATE app_settings SET deadline_days_before = #{days} WHERE id = 1")
    int updateDeadlineDaysBefore(@Param("days") int days);
}
