DELETE FROM rss_feed
WHERE url NOT IN (
                  'https://inside.java/feed.xml',
                  'https://www.youtube.com/feeds/videos.xml?playlist_id=UUSHmRtPmgnQ04CMUpSUqPfhxQ'
    );
