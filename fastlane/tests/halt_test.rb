# Run: ruby fastlane/tests/halt_test.rb (using the same gem environment as fastlane).
require_relative "test_helper"

cases = []
[0.1, 0.37, 0.999].each do |fraction|
  cases << ["preserves fraction #{fraction}", release("inProgress", fraction), nil, nil]
end
cases.concat([
  ["completed release", release("completed"), "completed", nil],
  ["already halted", release("halted", 0.1), "inProgress", nil],
  ["draft release", release("draft"), "inProgress", nil],
  ["missing release", nil, "not found", nil],
  ["wrong version", release("inProgress", 0.1, ["999"]), "not found", nil],
  ["missing fraction", release("inProgress"), "fraction", nil],
  ["zero fraction", release("inProgress", 0.0), "fraction", nil],
  ["full fraction", release("inProgress", 1.0), "fraction", nil],
  ["update error", release("inProgress", 0.1), "update failed", :update],
  ["commit error", release("inProgress", 0.1), "commit failed", :commit],
  ["update and cleanup error", release("inProgress", 0.1), "update failed", [:update, :abort]],
  ["commit and cleanup error", release("inProgress", 0.1), "commit failed", [:commit, :abort]],
  ["multi-code release", release("inProgress", 0.25, ["122", "123"]), nil, nil]
])

failures = []
Tempfile.create(["fake-play", ".json"], ENV["PAPERCLIP_RUN_SCRATCH_DIR"] || ENV["PAPERCLIP_SCRATCH_DIR"]) do |key|
  key.write("{}")
  key.flush
  ENV["PLAY_JSON_KEY_FILE"] = key.path
  cases.each do |name, target, expected_error, failure|
    begin
      previous = release("completed", nil, ["121"])
      track = AndroidPublisher::Track.new(track: "production", releases: [previous, target].compact)
      before = previous.to_h
      target_before = target&.to_h
      fraction = target&.user_fraction
      play = FakePlay.new(track, failure: failure)
      fake_client(play)
      TEST_WARNINGS.clear
      error = nil
      begin
        TEST_FASTFILE.runner.lanes[:android][:halt].call(version_code: "123")
      rescue StandardError => e
        error = e
      end
      if expected_error
        assert(error && error.message.include?(expected_error), "Expected #{expected_error.inspect}, got #{error.inspect}")
        if Array(failure).include?(:commit)
          assert(error.message.include?("may already have been applied") && error.message.include?("Re-read"), "Commit error must tell the operator to re-read the track")
        end
        if Array(failure).include?(:abort)
          assert(TEST_WARNINGS.any? { |message| message.include?("abort failed") }, "Cleanup failure must be a warning")
          assert(error.equal?(play.errors[:update]), "Cleanup replaced the original error") if Array(failure).include?(:update)
        end
        assert(play.calls.last&.first == :abort, "Failed halt must discard its edit")
        assert(play.calls.none? { |call| call.first == :commit }, "Invalid release must not commit") unless Array(failure).include?(:commit)
        assert(play.calls.none? { |call| call.first == :update }, "Invalid release must not update") unless failure
      else
        assert(error.nil?, "Unexpected error: #{error.inspect}")
        assert(play.calls.map(&:first) == [:begin, :read, :update, :commit], "Expected a real track update and commit, got #{play.calls.inspect}")
        assert(target.status == "halted", "Release must be halted")
        assert(target.user_fraction == fraction, "Rollout fraction changed")
        assert(target.to_h == target_before.merge(status: "halted"), "Release fields other than status changed")
        assert(play.updated_track.releases.include?(target), "Requested release missing from update")
        assert(previous.to_h == before, "Unrelated release changed")
        assert(play.calls.first[1] == "com.kimjisub.launchpad", "Wrong package")
      end
      puts "PASS: #{name}"
    rescue StandardError => e
      failures << name
      warn "FAIL: #{name}: #{e.message}"
    end
  end
end
puts "#{cases.length} cases, #{failures.length} failures"
exit(failures.empty? ? 0 : 1)
