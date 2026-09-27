package jp.bk.shiftmanager.mapper;

import java.util.List;
import jp.bk.shiftmanager.dto.StaffRow;
import jp.bk.shiftmanager.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Update("""
            UPDATE users SET password_hash = #{hash}, must_change_password = #{mustChange}, updated_at = now()
            WHERE id = #{id}
            """)
    int updatePassword(@Param("id") long id, @Param("hash") String hash, @Param("mustChange") boolean mustChange);

    /** 有効なスタッフが先、ポジションの表示順、名前の順 */
    @Select("""
            SELECT u.id, u.login_id, u.name, u.admin, u.enabled, p.name AS position_name
            FROM users u LEFT JOIN positions p ON p.id = u.position_id
            ORDER BY u.enabled DESC, p.display_order NULLS LAST, u.name
            """)
    List<StaffRow> findStaffRows();

    @Update("""
            UPDATE users SET login_id = #{loginId}, name = #{name}, position_id = #{positionId},
                   admin = #{admin}, updated_at = now()
            WHERE id = #{id}
            """)
    int updateProfile(User user);

    @Update("UPDATE users SET enabled = #{enabled}, updated_at = now() WHERE id = #{id}")
    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    /** 同じログインIDの件数（excludeIdは更新時の自分自身を除外するため。新規時は0） */
    @Select("SELECT COUNT(*) FROM users WHERE login_id = #{loginId} AND id <> #{excludeId}")
    long countByLoginId(@Param("loginId") String loginId, @Param("excludeId") long excludeId);

    /** 全スタッフ（無効を含む。名前の順） */
    @Select("SELECT * FROM users ORDER BY name, id")
    List<User> findAll();
}
