import argparse
import json
import sys
import os

try:
    import torch
    from faster_whisper import WhisperModel
except ImportError as e:
    print(f"Error importing dependencies: {e}", file=sys.stderr)
    print("Please ensure torch and faster-whisper are installed in your Python environment.", file=sys.stderr)
    sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description="Julius AI Clipper - Native Whisper Transcription Bridge")
    parser.add_argument("--audio", required=True, help="Path to input 16kHz WAV audio file")
    parser.add_argument("--output", required=True, help="Path to save output JSON segments")
    parser.add_argument("--model", default="large-v3-turbo", help="Whisper model size to use")
    args = parser.parse_args()

    if not os.path.exists(args.audio):
        print(f"Error: Audio file not found: {args.audio}", file=sys.stderr)
        sys.exit(1)

    print(f"Determining hardware acceleration...", flush=True)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    
    # Use int8 compute type as requested for quantization efficiency
    compute_type = "int8"
    if device == "cuda":
        # On GPU we can also choose float16 or int8_float16
        compute_type = "int8_float16"
        
    model_name = args.model
    print(f"Initializing WhisperModel('{model_name}') on device={device} with compute_type={compute_type}...", flush=True)
    try:
        model = WhisperModel(model_name, device=device, compute_type=compute_type)
    except ValueError as ve:
        if model_name == "large-v3-turbo":
            # Fallback to direct HuggingFace repo ID for faster-whisper versions that don't have turbo shortcut
            model_name = "deepdml/faster-whisper-large-v3-turbo-ct2"
            print(f"Model ID '{model_name}' not registered in local registry. Fetching from Hugging Face: {model_name}...", flush=True)
            try:
                model = WhisperModel(model_name, device=device, compute_type=compute_type)
            except Exception as e:
                print(f"Failed to initialize Hugging Face fallback model: {e}", file=sys.stderr)
                print("Retrying initialization on CPU with int8 quantization...", flush=True)
                model = WhisperModel(model_name, device="cpu", compute_type="int8")
        else:
            print(f"Failed to initialize model '{model_name}': {ve}", file=sys.stderr)
            print("Retrying initialization on CPU with int8 quantization...", flush=True)
            model = WhisperModel(model_name, device="cpu", compute_type="int8")
    except Exception as e:
        print(f"Failed to initialize WhisperModel: {e}", file=sys.stderr)
        # Fallback to CPU + int8
        if model_name == "large-v3-turbo":
            model_name = "deepdml/faster-whisper-large-v3-turbo-ct2"
        print(f"Retrying initialization on CPU with int8 using model: {model_name}...", flush=True)
        model = WhisperModel(model_name, device="cpu", compute_type="int8")

    print(f"Transcribing audio track: {args.audio}", flush=True)
    try:
        segments, info = model.transcribe(args.audio, beam_size=5, word_timestamps=False)
        
        output_segments = []
        for segment in segments:
            output_segments.append({
                "start": segment.start,
                "end": segment.end,
                "text": segment.text
            })

        # Ensure parent directory for output exists
        os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
        
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(output_segments, f, ensure_ascii=False, indent=2)

        print(f"Transcription complete. Saved {len(output_segments)} segments.", flush=True)
    except Exception as e:
        print(f"Error during audio transcription or serialization: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
