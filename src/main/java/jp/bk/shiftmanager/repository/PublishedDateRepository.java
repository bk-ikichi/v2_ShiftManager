package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
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
}
