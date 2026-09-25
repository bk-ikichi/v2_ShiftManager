package jp.bk.shiftmanager.service;

import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingService {

    private static final String INVALID_DAYS = "締切日数は0〜30の整数で入力してください";

    private final AppSettingRepository appSettingRepository;

    /** 締切＝サイクル開始日の何日前か */
    public int getDeadlineDaysBefore() {
        return appSettingRepository.getDeadlineDaysBefore();
    }

    /** 画面入力の文字列を検証して保存する */
    @Transactional
    public void updateDeadlineDaysBefore(String input) {
        int days;
        try {
            days = Integer.parseInt(input == null ? "" : input.strip());
        } catch (NumberFormatException e) {
            throw new BusinessException(INVALID_DAYS);
        }
        if (days < 0 || days > 30) {
            throw new BusinessException(INVALID_DAYS);
        }
        appSettingRepository.updateDeadlineDaysBefore(days);
    }
}
