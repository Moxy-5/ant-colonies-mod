"""The raid march film made into videos for the user (2026-11-08).

    ./gradlew runFilm -PfilmScene=tools/film/raid_march.film
    python tools/raid_film.py

The scene (tools/film/raid_march.film) takes three pictures every other game tick: wide (the hump and the trail's
start, a fixed camera), side (riding along south of the column) and top (straight down over it). This makes one video
of each, run-film/raid_march_<view>.mp4, and the three stacked one over another, run-film/raid_march.mp4 (portrait,
for a phone), all at real speed (10 pictures a second). Needs ffmpeg.
"""
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SHOTS = ROOT / "run-film" / "screenshots" / "raid_march"
OUT = ROOT / "run-film"
FPS = 10  # (a picture every other tick of 20 a second: real speed)
VIEWS = ("wide", "side", "top")


def ffmpeg(*args):
    subprocess.run(["ffmpeg", "-v", "error", "-y", *args], check=True)


def main():
    for view in VIEWS:
        ffmpeg("-framerate", str(FPS), "-i", str(SHOTS / f"{view}_%03d.png"), "-c:v", "libx264", "-pix_fmt", "yuv420p",
               str(OUT / f"raid_march_{view}.mp4"))
    inputs = []
    for view in VIEWS:
        inputs += ["-framerate", str(FPS), "-i", str(SHOTS / f"{view}_%03d.png")]
    scaled = ";".join(f"[{n}]scale=640:360[v{n}]" for n in range(len(VIEWS)))
    stack = "".join(f"[v{n}]" for n in range(len(VIEWS))) + f"vstack=inputs={len(VIEWS)}"
    ffmpeg(*inputs, "-filter_complex", scaled + ";" + stack, "-c:v", "libx264", "-pix_fmt", "yuv420p",
           str(OUT / "raid_march.mp4"))
    print("made", ", ".join(str(OUT / f"raid_march_{view}.mp4") for view in VIEWS), "and", OUT / "raid_march.mp4")


if __name__ == "__main__":
    main()
