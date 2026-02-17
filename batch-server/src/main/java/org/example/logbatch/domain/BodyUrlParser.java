package org.example.logbatch.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

public class BodyUrlParser {

    private static final String S3_PREFIX = "s3://";
    private static final List<String> SUFFIXES = List.of(".req", ".res", ".req.header", ".res.header");

    @Getter
    @AllArgsConstructor
    public static class Result {
        private final String bucket;
        private final String objectPrefix;

        public List<String> getObjectKeys() {
            return SUFFIXES.stream()
                    .map(suffix -> objectPrefix + suffix)
                    .toList();
        }
    }

    public static Result parse(String bodyUrl) {
        if (bodyUrl == null || bodyUrl.isBlank()) {
            return null;
        }

        String path = bodyUrl;
        if (path.startsWith(S3_PREFIX)) {
            path = path.substring(S3_PREFIX.length());
        }

        int firstSlash = path.indexOf('/');
        if (firstSlash <= 0 || firstSlash == path.length() - 1) {
            return null;
        }

        String bucket = path.substring(0, firstSlash);
        String objectPrefix = path.substring(firstSlash + 1);

        return new Result(bucket, objectPrefix);
    }
}