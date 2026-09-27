package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.mapper.PublishedDateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PublishedDateRepository {

    private final PublishedDateMapper publishedDateMapper;

    public boolean isPublished(LocalDate date) {
        return publishedDateMapper.exists(date);
    }

    /** 公開済みなら何もしない */
    public void publish(LocalDate date) {
        publishedDateMapper.insert(date);
    }

    /** 期間内の公開済みの日（日付順） */
    public List<LocalDate> findDates(LocalDate from, LocalDate to) {
        return publishedDateMapper.findBetween(from, to);
    }
}
