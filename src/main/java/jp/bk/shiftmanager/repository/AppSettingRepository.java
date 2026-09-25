package jp.bk.shiftmanager.repository;

import jp.bk.shiftmanager.mapper.AppSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AppSettingRepository {

    private final AppSettingMapper appSettingMapper;

    public int getDeadlineDaysBefore() {
        return appSettingMapper.getDeadlineDaysBefore();
    }

    public void updateDeadlineDaysBefore(int days) {
        appSettingMapper.updateDeadlineDaysBefore(days);
    }
}
