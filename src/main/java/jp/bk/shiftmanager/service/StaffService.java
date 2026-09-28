package jp.bk.shiftmanager.service;

import java.util.List;
import jp.bk.shiftmanager.auth.StaffInitialPassword;
import jp.bk.shiftmanager.auth.TempPasswords;
import jp.bk.shiftmanager.dto.StaffRow;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.StaffCreateForm;
import jp.bk.shiftmanager.form.StaffEditForm;
import jp.bk.shiftmanager.repository.PositionRepository;
import jp.bk.shiftmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffInitialPassword staffInitialPassword;

    public List<StaffRow> findAll() {
        return userRepository.findStaffRows();
    }

    /** スタッフを登録する。初期パスワードが空欄なら共通の固定値を使い、初回ログイン時にパスワード変更が必要な状態で作成する */
    @Transactional
    public long create(StaffCreateForm form) {
        checkLoginIdUnique(form.getLoginId(), 0);
        checkPosition(form.getPositionId());
        String password = (form.getPassword() == null || form.getPassword().isEmpty())
                ? staffInitialPassword.value()
                : form.getPassword();
        User user = new User();
        user.setLoginId(form.getLoginId());
        user.setName(form.getName());
        user.setPositionId(form.getPositionId());
        user.setAdmin(form.isAdmin());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setMustChangePassword(true);
        userRepository.insert(user);
        return user.getId();
    }

    public StaffEditForm editForm(long id) {
        User user = find(id);
        StaffEditForm form = new StaffEditForm();
        form.setLoginId(user.getLoginId());
        form.setName(user.getName());
        form.setPositionId(user.getPositionId());
        form.setAdmin(user.isAdmin());
        return form;
    }

    @Transactional
    public void update(long id, StaffEditForm form, long actorId) {
        User user = find(id);
        if (id == actorId && !form.isAdmin()) {
            throw new BusinessException("自分の管理者権限は外せません");
        }
        checkLoginIdUnique(form.getLoginId(), id);
        checkPosition(form.getPositionId());
        user.setLoginId(form.getLoginId());
        user.setName(form.getName());
        user.setPositionId(form.getPositionId());
        user.setAdmin(form.isAdmin());
        userRepository.updateProfile(user);
    }

    /** 管理者による仮パスワードへのリセット。生成した仮パスワードを返す。次回ログイン時に変更が必要になる */
    @Transactional
    public String resetPassword(long id) {
        find(id);
        String tempPassword = TempPasswords.generate();
        userRepository.updatePassword(id, passwordEncoder.encode(tempPassword), true);
        return tempPassword;
    }

    @Transactional
    public void setEnabled(long id, boolean enabled, long actorId) {
        find(id);
        if (id == actorId && !enabled) {
            throw new BusinessException("自分自身は無効化できません");
        }
        userRepository.updateEnabled(id, enabled);
    }

    private User find(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("スタッフが見つかりません"));
    }

    private void checkLoginIdUnique(String loginId, long excludeId) {
        if (userRepository.existsLoginId(loginId, excludeId)) {
            throw new BusinessException("このログインIDは既に使われています");
        }
    }

    private void checkPosition(Long positionId) {
        if (positionId != null && positionRepository.findById(positionId).isEmpty()) {
            throw new BusinessException("ポジションが見つかりません");
        }
    }
}
