# Run: ruby fastlane/tests/deploy_promote_test.rb (using the same gem environment as fastlane).
require_relative "test_helper"

# Stop after real fastlane option and upload configuration checks.
# Rejected options must never reach a build, version bump or Play operation.
operations = []
real_upload = TEST_FASTFILE.method(:upload_to_play_store)
aab = Tempfile.new(["fake-build", ".aab"])
key = Tempfile.new(["fake-play", ".json"])
key.write("{}")
key.flush
at_exit { aab.close!; key.close! }
TEST_FASTFILE.define_singleton_method(:upload_to_play_store) do |**options|
  action = Fastlane::Actions::UploadToPlayStoreAction
  Supply.config = FastlaneCore::Configuration.create(action.available_options, options)
  Supply::Uploader.new.verify_config!
  operations << [:upload, options]
end
TEST_FASTFILE.define_singleton_method(:gradle) { |**options| operations << [:gradle, options] }
TEST_FASTFILE.define_singleton_method(:bump_version_code!) { operations << [:bump] }
TEST_FASTFILE.define_singleton_method(:lane_context) do
  { Fastlane::Actions::SharedValues::GRADLE_AAB_OUTPUT_PATH => aab.path }
end
ENV["PLAY_JSON_KEY_FILE"] = key.path

cases = []
[10, "10", 50, "50", 1.5, "1.5", 0, "0", -0.1, "-0.1", "abc", "0.5oops", "", nil, false, "NaN", "Infinity"].each do |value|
  cases << [:promote, { fraction: value }, :invalid]
  %w[production internal].each do |track|
    cases << [:deploy, { track: track, rollout: value }, :invalid]
  end
end
cases << [:deploy, { track: "production" }, :invalid]
[0.1, "0.1", 1, "1"].each do |value|
  cases << [:promote, { fraction: value }, Float(value)]
  cases << [:deploy, { track: "production", rollout: value }, Float(value)]
  %w[internal alpha beta].each do |track|
    cases << [:deploy, { track: track, rollout: value }, :unsupported]
  end
  cases << [:deploy, { rollout: value }, :unsupported]
end
cases << [:promote, {}, 0.1]
%w[internal alpha beta].each { |track| cases << [:deploy, { track: track }, nil] }
cases << [:deploy, {}, nil]

failures = []
cases.each do |lane, options, expected|
  name = "#{lane} #{options.inspect}"
  begin
    operations.clear
    TEST_SUCCESSES.clear
    error = nil
    begin
      TEST_FASTFILE.runner.lanes[:android][lane].call(options)
    rescue StandardError => e
      error = e
    end
    if [:invalid, :unsupported].include?(expected)
      assert(operations.empty?, "Invalid fraction reached build or Play: #{operations.inspect}")
      message = expected == :invalid ? "Pass fraction as a decimal between 0 and 1 (e.g. 0.5 = 50%, 1 = complete)" : "Pass rollout only for the production track"
      assert(error && error.message.include?(message), "Expected #{message.inspect}, got #{error.inspect}")
      assert(TEST_SUCCESSES.empty?, "Rejected fraction reported success")
    else
      assert(error.nil?, "Unexpected error: #{error.inspect}")
      uploads = operations.select { |step| step.first == :upload }
      assert(uploads.size == 1, "Expected one upload, got #{operations.inspect}")
      upload = uploads.first[1]
      status = expected == 1 ? "completed" : "inProgress"
      if lane == :promote
        assert(operations.map(&:first) == [:upload], "Promotion must not build")
        assert(upload[:track] == "internal" && upload[:track_promote_to] == "production", "Wrong promotion tracks")
        assert(upload[:track_promote_release_status] == status, "Wrong promotion status")
      else
        status = "draft" unless options[:track] == "production"
        assert(operations.map(&:first) == [:bump, :gradle, :gradle, :gradle, :upload], "Deploy build sequence changed")
        assert(upload[:track] == (options[:track] || "internal"), "Wrong deploy track")
        assert(upload[:release_status] == status, "Wrong deploy status")
      end
      assert(upload[:rollout].is_a?(String), "Supply rollout must be a String") unless expected.nil?
      actual_fraction = upload[:rollout] && Float(upload[:rollout])
      assert(actual_fraction == expected, "Expected fraction #{expected.inspect}, got #{upload[:rollout].inspect}")
      assert(TEST_SUCCESSES.size == 1, "Successful upload must report success once")
    end
    puts "PASS: #{name}"
  rescue StandardError => e
    failures << name
    warn "FAIL: #{name}: #{e.message}"
  end
end
# Run the real action and uploader too, replacing only the remote Play service.
# This catches changes in option types and the resulting production release.
promotion_values = [0.1, "0.1", 1, "1"]
promotion_values.each do |value|
  name = "real promotion #{value.inspect}"
  begin
    track = AndroidPublisher::Track.new(track: "internal", releases: [release("completed")])
    play = FakePlay.new(track)
    fake_client(play)
    TEST_FASTFILE.define_singleton_method(:upload_to_play_store, real_upload)
    TEST_FASTFILE.runner.lanes[:android][:promote].call(version_code: "123", fraction: value)
    assert(play.calls.map(&:first) == [:begin, :read, :read, :update, :commit], "Promotion did not commit a Play update: #{play.calls.inspect}")
    assert(play.calls.find { |step| step.first == :update }[3] == "production", "Wrong destination track")
    promoted = play.updated_track.releases.first
    expected_fraction = Float(value) == 1 ? nil : Float(value)
    assert(promoted.status == (expected_fraction ? "inProgress" : "completed"), "Wrong promoted status")
    assert(promoted.user_fraction == expected_fraction, "Wrong promoted fraction")
    assert(promoted.version_codes == ["123"], "Wrong promoted version")
    puts "PASS: #{name}"
  rescue StandardError => e
    failures << name
    warn "FAIL: #{name}: #{e.message}"
  end
end
puts "#{cases.length + promotion_values.length} cases, #{failures.length} failures"
exit(failures.empty? ? 0 : 1)
