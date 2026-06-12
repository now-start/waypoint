package org.nowstart.waypoint.adapter.out.tago;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class TagoResponseParserTest {

    private final TagoResponseParser parser = new TagoResponseParser(new ObjectMapper());

    @Test
    @DisplayName("items.item 배열 응답을 목록으로 변환한다")
    void parseArrayItems() {
        // given: items.item이 배열인 TAGO 응답
        String response = """
                {
                  "response": {
                    "header": {
                      "resultCode": "00",
                      "resultMsg": "NORMAL SERVICE."
                    },
                    "body": {
                      "totalCount": 2,
                      "items": {
                        "item": [
                          {"routeid": "R1", "routeno": "101"},
                          {"routeid": "R2", "routeno": "102"}
                        ]
                      }
                    }
                  }
                }
                """;

        // when: 응답을 파싱한다
        TagoResponseParser.ParsedTagoResponse parsed = parser.parse(response);

        // then: 배열 항목을 그대로 목록으로 변환한다
        then(parsed.isSuccess()).isTrue();
        then(parsed.totalCount()).isEqualTo(2);
        then(parsed.items()).hasSize(2);
        then(TagoResponseParser.text(parsed.items().getFirst(), "routeid")).isEqualTo("R1");
    }

    @Test
    @DisplayName("items.item 단건 응답을 목록으로 변환한다")
    void parseSingleItem() {
        // given: items.item이 단건 객체인 TAGO 응답
        String response = """
                {
                  "response": {
                    "header": {
                      "resultCode": "00",
                      "resultMsg": "NORMAL SERVICE."
                    },
                    "body": {
                      "totalCount": 1,
                      "items": {
                        "item": {"citycode": "38010", "cityname": "창원시"}
                      }
                    }
                  }
                }
                """;

        // when: 응답을 파싱한다
        TagoResponseParser.ParsedTagoResponse parsed = parser.parse(response);

        // then: 단건 항목도 목록으로 감싸서 변환한다
        then(parsed.items()).hasSize(1);
        then(TagoResponseParser.text(parsed.items().getFirst(), "cityCode")).isEqualTo("38010");
        then(TagoResponseParser.text(parsed.items().getFirst(), "cityName")).isEqualTo("창원시");
    }

    @Test
    @DisplayName("도착 남은 초를 분과 예상 시각으로 변환한다")
    void parseArrivalTime() {
        // given: 도착 남은 시간이 초 단위로 내려오는 응답
        String response = """
                {
                  "response": {
                    "header": {"resultCode": "00"},
                    "body": {
                      "items": {
                        "item": {"arrtime": "125"}
                      }
                    }
                  }
                }
                """;

        // when: 응답을 파싱한다
        TagoResponseParser.ParsedTagoResponse parsed = parser.parse(response);

        // then: 남은 초를 올림 분 단위로 변환한다
        then(TagoResponseParser.arrivalRemainingMinutes(parsed.items().getFirst())).isEqualTo(3);
    }

    @Test
    @DisplayName("결과코드가 없는 응답은 성공으로 처리하지 않는다")
    void parseMissingResultCodeAsFailure() {
        // given: resultCode가 없는 비정상 TAGO 응답
        String response = """
                {
                  "response": {
                    "body": {
                      "items": {
                        "item": []
                      }
                    }
                  }
                }
                """;

        // when: 응답을 파싱한다
        TagoResponseParser.ParsedTagoResponse parsed = parser.parse(response);

        // then: 성공 응답으로 취급하지 않는다
        then(parsed.isSuccess()).isFalse();
    }
}
