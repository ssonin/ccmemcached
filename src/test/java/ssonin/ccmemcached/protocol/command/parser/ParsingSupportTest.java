package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.error.ApplicationError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKey;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseNoReply;

class ParsingSupportTest {

  @Nested
  class ParseKey {

    @Test
    void returns_key_when_valid() {
      // given
      var key = "mykey";

      // when
      var parsedKey = parseKey(key);

      // then
      assertThat(parsedKey).isEqualTo(key);
    }

    @Test
    void throws_on_empty_key() {
      // given
      var key = "";

      // when
      var thrown = catchThrowable(() -> parseKey(key));

      // then
      assertThat(thrown).isInstanceOf(ApplicationError.class)
        .hasMessageStartingWith("CLIENT_ERROR");
    }

    @Test
    void throws_on_key_exceeding_max_length() {
      // given
      var key = "a".repeat(251);

      // when
      var thrown = catchThrowable(() -> parseKey(key));

      // then
      assertThat(thrown).isInstanceOf(ApplicationError.class)
        .hasMessageStartingWith("CLIENT_ERROR");
    }

    @Test
    void throws_on_key_with_control_character() {
      // given
      var key = "my\u0001key";

      // when
      var thrown = catchThrowable(() -> parseKey(key));

      // then
      assertThat(thrown).isInstanceOf(ApplicationError.class)
        .hasMessageStartingWith("CLIENT_ERROR");
    }

    @Test
    void throws_on_key_with_delete_character() {
      // given
      var key = "my\u007fkey";

      // when
      var thrown = catchThrowable(() -> parseKey(key));

      // then
      assertThat(thrown).isInstanceOf(ApplicationError.class)
        .hasMessageStartingWith("CLIENT_ERROR");
    }
  }

  @Nested
  class ParseNoReply {

    @Test
    void returns_false_when_noreply_is_absent() {
      // given
      var parts = new String[]{"set", "mykey", "0", "900", "5"};

      // when
      var noReply = parseNoReply(parts, 5);

      // then
      assertThat(noReply).isFalse();
    }

    @Test
    void returns_true_when_noreply_is_present() {
      // given
      var parts = new String[]{"set", "mykey", "0", "900", "5", "noreply"};

      // when
      var noReply = parseNoReply(parts, 5);

      // then
      assertThat(noReply).isTrue();
    }

    @Test
    void throws_on_invalid_trailing_token() {
      // given
      var parts = new String[]{"set", "mykey", "0", "900", "5", "NOREPLY"};

      // when
      var thrown = catchThrowable(() -> parseNoReply(parts, 5));

      // then
      assertThat(thrown).isInstanceOf(ApplicationError.class)
        .hasMessageStartingWith("CLIENT_ERROR");
    }

    @Test
    void supports_other_commands_with_different_noreply_positions() {
      // given
      var parts = new String[]{"delete", "mykey", "noreply"};

      // when
      var noReply = parseNoReply(parts, 2);

      // then
      assertThat(noReply).isTrue();
    }
  }
}
