package jp.bk.shiftmanager.mapper;

import jp.bk.shiftmanager.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE id = #{id}")
    User findById(@Param("id") long id);

    @Select("SELECT * FROM users WHERE login_id = #{loginId}")
    User findByLoginId(@Param("loginId") String loginId);

    @Select("SELECT COUNT(*) FROM users")
    long count();

    @Insert("""
            INSERT INTO users (login_id, name, password_hash, position_id, admin, enabled, must_change_password)
            VALUES (#{loginId}, #{name}, #{passwordHash}, #{positionId}, #{admin}, #{enabled}, #{mustChangePassword})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(User user);
}
