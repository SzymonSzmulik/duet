import Cocoa
import MediaPlayer

// Duet's Now Playing helper (macOS). Duet writes one JSON line per change to stdin; this publishes it to the system's
// Now Playing (Control Center, the media keys, notch apps) and prints the commands people press, one per line.
setbuf(stdout, nil)
let app = NSApplication.shared
app.setActivationPolicy(.accessory)
func send(_ s: String) { print(s); fflush(stdout) }

let commands = MPRemoteCommandCenter.shared()
commands.playCommand.addTarget { _ in send("play"); return .success }
commands.pauseCommand.addTarget { _ in send("pause"); return .success }
commands.togglePlayPauseCommand.addTarget { _ in send("toggle"); return .success }
commands.nextTrackCommand.addTarget { _ in send("next"); return .success }
commands.previousTrackCommand.addTarget { _ in send("previous"); return .success }
commands.changePlaybackPositionCommand.addTarget { e in
    if let e = e as? MPChangePlaybackPositionCommandEvent { send("seek \(e.positionTime)") }
    return .success
}

func apply(_ o: [String: Any]) {
    let center = MPNowPlayingInfoCenter.default()
    if o["clear"] as? Bool == true { center.nowPlayingInfo = nil; center.playbackState = .stopped; return }
    var info = [String: Any]()
    if let v = o["title"] as? String { info[MPMediaItemPropertyTitle] = v }
    if let v = o["artist"] as? String { info[MPMediaItemPropertyArtist] = v }
    if let v = o["album"] as? String { info[MPMediaItemPropertyAlbumTitle] = v }
    if let v = o["duration"] as? Double { info[MPMediaItemPropertyPlaybackDuration] = v }
    if let v = o["position"] as? Double { info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = v }
    let playing = o["playing"] as? Bool ?? false
    info[MPNowPlayingInfoPropertyPlaybackRate] = playing ? 1.0 : 0.0
    if let path = o["artwork"] as? String, let image = NSImage(contentsOfFile: path) {
        info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
    }
    center.nowPlayingInfo = info
    center.playbackState = playing ? .playing : .paused
}

DispatchQueue.global().async {
    while let line = readLine() {
        guard let data = line.data(using: .utf8), let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { continue }
        DispatchQueue.main.async { apply(o) }
    }
    exit(0)   // Duet closed
}
app.run()
