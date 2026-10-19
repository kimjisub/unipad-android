# Run: ruby fastlane/tests/rollout_test.rb (using the same gem environment as fastlane).
require_relative "test_helper"

cases = []
%w[halted inProgress].each do |status|
  [0.1, 0.5, 1.0].each do |fraction|
    cases << ["#{status} to #{fraction}", release(status, 0.1), fraction, nil, nil]
  end
  cases << ["#{status} decrease", release(status, 0.5), 0.1, "lower", nil]
end
cases.concat([
  ["completed release", release("completed"), 0.5, "inProgress or halted", nil],
  ["draft release", release("draft"), 0.5, "inProgress or halted", nil],
  ["missing release", nil, 0.5, "not found", nil],
  ["wrong version", release("halted", 0.1, ["999"]), 0.5, "not found", nil],
  ["CLI fraction", release("halted", 0.1), "0.37", nil, nil],
  ["numeric version codes", release("halted", 0.1, [122, 123]), 0.5, nil, nil],
  ["multi-code release", release("halted", 0.25, ["122", "123"]), 0.5, nil, nil],
  ["missing current fraction", release("halted"), 0.5, "staged rollout fraction", nil],
  ["invalid current fraction", release("inProgress", 1.0), 1, "staged rollout fraction", nil],
  ["begin error", release("halted", 0.1), 0.5, "begin failed", :begin],
  ["read error", release("halted", 0.1), 0.5, "read failed", :read],
  ["update error", release("halted", 0.1), 0.5, "update failed", :update],
  ["commit error", release("halted", 0.1), 0.5, "commit failed", :commit],
  ["update and cleanup error", release("halted", 0.1), 0.5, "update failed", [:update, :abort]],
  ["commit and cleanup error", release("halted", 0.1), 0.5, "commit failed", [:commit, :abort]],
  ["invalid release and cleanup error", release("draft"), 0.5, "inProgress or halted", :abort]
])
# A percentage typed as a whole number (10 for 10%) must not complete the rollout.
[nil, "", "garbage", "0.5oops", 0, -0.1, "NaN", "Infinity", 1.5, "10", "50"].each do |fraction|
  cases << ["invalid requested fraction #{fraction.inspect}", release("halted", 0.1), fraction, "decimal between 0 and 1 (e.g. 0.5 = 50%", nil]
end

failures = []
Tempfile.create(["fake-play", ".json"]) do |key|
  key.write("{}")
  key.flush
  ENV["PLAY_JSON_KEY_FILE"] = key.path
  cases.each do |name, target, fraction, expected_error, failure|
    begin
      previous = release("completed", nil, ["121"])
      track = AndroidPublisher::Track.new(track: "production", releases: [previous, target].compact)
      previous_before = previous.to_h
      target_before = target&.to_h
      play = FakePlay.new(track, failure: failure)
      client = fake_client(play)
      TEST_WARNINGS.clear
      TEST_SUCCESSES.clear
      error = nil
      begin
        TEST_FASTFILE.runner.lanes[:android][:rollout].call(version_code: "123", fraction: fraction)
      rescue StandardError => e
        error = e
      end
      steps = play.calls.map(&:first)
      if expected_error
        assert(error && error.message.include?(expected_error), "Expected #{expected_error.inspect}, got #{error.inspect}")
        if Array(failure).include?(:commit)
          assert(error.message.include?("may already have been applied") && error.message.include?("Re-read"), "Commit error must tell the operator to re-read the track")
        elsif Array(failure).include?(:update)
          assert(!steps.include?(:commit), "Failed update must not commit")
        else
          assert(!steps.include?(:update) && !steps.include?(:commit), "Invalid release or fraction must not update or commit")
          assert(target&.to_h == target_before, "Rejected release was changed")
        end
        assert(steps.empty?, "Invalid requested fraction must be refused before reading the track") if name.start_with?("invalid requested fraction")
        if steps.include?(:begin) && !Array(failure).include?(:begin)
          assert(steps.last == :abort, "Failed rollout must discard its edit")
        end
        if Array(failure).include?(:abort)
          assert(TEST_WARNINGS.any? { |message| message.include?("abort failed") }, "Cleanup failure must be a warning")
          assert(error.equal?(play.errors[:update]), "Cleanup replaced the original error") if Array(failure).include?(:update)
        end
      else
        assert(error.nil?, "Unexpected error: #{error.inspect}")
        assert(steps == [:begin, :read, :update, :commit], "Expected a track update and commit, got #{steps.inspect}")
        requested_fraction = Float(fraction)
        expected_status = requested_fraction >= 1 ? "completed" : "inProgress"
        expected_fraction = requested_fraction >= 1 ? nil : requested_fraction
        assert(target.status == expected_status, "Wrong release status")
        assert(target.user_fraction == expected_fraction, "Wrong rollout fraction")
        expected_release = target_before.reject { |key, _| key == :user_fraction }.merge(status: expected_status)
        actual_release = target.to_h.reject { |key, _| key == :user_fraction }
        assert(actual_release == expected_release, "Unrelated release fields changed")
        assert(JSON.parse(target.to_json)["userFraction"].nil?, "Completed release still sends a fraction value") if requested_fraction >= 1
        # Completing replaces the previous full release; a staged rollout keeps it serving everyone else.
        expected_releases = requested_fraction >= 1 ? [target] : [previous, target]
        assert(play.updated_track.releases == expected_releases, "Expected releases #{expected_releases.map(&:version_codes)}, got #{play.updated_track.releases.map(&:version_codes)}")
        assert(client.current_edit.nil?, "Successful commit left an edit open")
        expected_result = requested_fraction >= 1 ? "completed" : requested_fraction.to_s
        assert(TEST_SUCCESSES == ["production rollout 123 -> #{expected_result}"], "Unexpected success message #{TEST_SUCCESSES.inspect}")
      end
      assert(previous.to_h == previous_before, "Unrelated release changed")
      assert(play.calls.first[1] == "com.kimjisub.launchpad", "Wrong package") unless play.calls.empty?
      puts "PASS: #{name}"
    rescue StandardError => e
      failures << name
      warn "FAIL: #{name}: #{e.message}"
    end
  end
end
puts "#{cases.length} cases, #{failures.length} failures"
exit(failures.empty? ? 0 : 1)
