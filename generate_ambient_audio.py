import wave
import struct
import math
import random
import os

SAMPLE_RATE = 44100
DURATION = 16.0  # 16 seconds for full natural acoustic cycles
TOTAL_SAMPLES = int(SAMPLE_RATE * DURATION)
FADE_SAMPLES = int(SAMPLE_RATE * 2.0) # 2.0s smooth crossfade for seamless infinite looping

def apply_crossfade(samples_left, samples_right):
    n = len(samples_left)
    out_l = list(samples_left)
    out_r = list(samples_right)
    
    # Smooth equal-power crossfade between start and end
    for i in range(FADE_SAMPLES):
        t = i / FADE_SAMPLES
        # equal power fade: cos/sin
        start_w = math.sin(t * math.pi * 0.5)
        end_w = math.cos(t * math.pi * 0.5)
        end_idx = n - FADE_SAMPLES + i
        
        blended_l = out_l[i] * start_w + out_l[end_idx] * end_w
        blended_r = out_r[i] * start_w + out_r[end_idx] * end_w
        
        out_l[i] = blended_l
        out_r[i] = blended_r
        out_l[end_idx] = blended_l
        out_r[end_idx] = blended_r
        
    return out_l, out_r

def write_wav(filename, samples_l, samples_r):
    samples_l, samples_r = apply_crossfade(samples_l, samples_r)
    
    # Soft peak normalization to -2 dB
    max_val = 1e-6
    for l, r in zip(samples_l, samples_r):
        max_val = max(max_val, abs(l), abs(r))
    
    gain = 0.78 / max_val
    
    with wave.open(filename, 'wb') as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        
        frames = bytearray()
        for l, r in zip(samples_l, samples_r):
            # Soft saturation limiter
            val_l_f = math.tanh(l * gain)
            val_r_f = math.tanh(r * gain)
            val_l = int(max(-1.0, min(1.0, val_l_f)) * 32767)
            val_r = int(max(-1.0, min(1.0, val_r_f)) * 32767)
            frames.extend(struct.pack('<hh', val_l, val_r))
        wf.writeframes(frames)
    print(f"Generated {filename} ({len(samples_l)} samples, {os.path.getsize(filename)} bytes)")

# 1. DEEP BROWN NOISE (Velvet warm rumble, deeply lowpassed)
def generate_brown_noise():
    samples_l = [0.0] * TOTAL_SAMPLES
    samples_r = [0.0] * TOTAL_SAMPLES
    
    l_acc1, l_acc2, l_acc3 = 0.0, 0.0, 0.0
    r_acc1, r_acc2, r_acc3 = 0.0, 0.0, 0.0
    
    for i in range(TOTAL_SAMPLES):
        w_l = random.uniform(-1.0, 1.0)
        w_r = random.uniform(-1.0, 1.0)
        
        # 3-stage steep low-pass integration
        l_acc1 = 0.988 * l_acc1 + 0.012 * w_l
        l_acc2 = 0.982 * l_acc2 + 0.018 * l_acc1
        l_acc3 = 0.975 * l_acc3 + 0.025 * l_acc2
        
        r_acc1 = 0.988 * r_acc1 + 0.012 * w_r
        r_acc2 = 0.982 * r_acc2 + 0.018 * r_acc1
        r_acc3 = 0.975 * r_acc3 + 0.025 * r_acc2
        
        samples_l[i] = l_acc3
        samples_r[i] = r_acc3
        
    return samples_l, samples_r

# 2. GENTLE RAIN (Soft, continuous relaxing rain shower)
def generate_rain():
    samples_l = [0.0] * TOTAL_SAMPLES
    samples_r = [0.0] * TOTAL_SAMPLES
    
    # Pink noise filter states
    b0_l, b1_l, b2_l, lp_l = 0.0, 0.0, 0.0, 0.0
    b0_r, b1_r, b2_r, lp_r = 0.0, 0.0, 0.0, 0.0
    
    drops = []
    
    for i in range(TOTAL_SAMPLES):
        w_l = random.uniform(-1.0, 1.0)
        w_r = random.uniform(-1.0, 1.0)
        
        # Gentle diffused pink noise rain bed
        b0_l = 0.994 * b0_l + w_l * 0.04
        b1_l = 0.965 * b1_l + w_l * 0.09
        b2_l = 0.880 * b2_l + w_l * 0.18
        pink_l = (b0_l + b1_l + b2_l) * 0.25
        lp_l = 0.85 * lp_l + 0.15 * pink_l # lowpass dampening for soft rain
        
        b0_r = 0.994 * b0_r + w_r * 0.04
        b1_r = 0.965 * b1_r + w_r * 0.09
        b2_r = 0.880 * b2_r + w_r * 0.18
        pink_r = (b0_r + b1_r + b2_r) * 0.25
        lp_r = 0.85 * lp_r + 0.15 * pink_r
        
        # Gentle realistic droplet frequency (~18 drops per second)
        if random.random() < (18.0 / SAMPLE_RATE):
            freq = random.uniform(1100.0, 2200.0)
            length = random.randint(int(SAMPLE_RATE * 0.010), int(SAMPLE_RATE * 0.030))
            pan = random.uniform(0.1, 0.9)
            amp = random.uniform(0.04, 0.14)
            drops.append([length, length, freq, pan, amp])
            
        drop_l = 0.0
        drop_r = 0.0
        
        active_drops = []
        for d in drops:
            rem, total_len, freq, pan, amp = d
            t = (total_len - rem) / SAMPLE_RATE
            env = math.exp(-8.0 * (1.0 - rem / total_len))
            sample = math.sin(2.0 * math.pi * freq * t) * env * amp
            drop_l += sample * (1.0 - pan)
            drop_r += sample * pan
            
            d[0] -= 1
            if d[0] > 0:
                active_drops.append(d)
        drops = active_drops
        
        samples_l[i] = lp_l * 0.85 + drop_l
        samples_r[i] = lp_r * 0.85 + drop_r
        
    return samples_l, samples_r

# 3. COZY FIREPLACE (Warm hearth rumble with natural sporadic wood crackles)
def generate_fireplace():
    samples_l = [0.0] * TOTAL_SAMPLES
    samples_r = [0.0] * TOTAL_SAMPLES
    
    lp_l1, lp_l2 = 0.0, 0.0
    lp_r1, lp_r2 = 0.0, 0.0
    
    cracks = []
    
    for i in range(TOTAL_SAMPLES):
        w_l = random.uniform(-1.0, 1.0)
        w_r = random.uniform(-1.0, 1.0)
        
        # Deep warm hearth fire bed (60Hz - 180Hz)
        lp_l1 = 0.986 * lp_l1 + 0.014 * w_l
        lp_l2 = 0.978 * lp_l2 + 0.022 * lp_l1
        
        lp_r1 = 0.986 * lp_r1 + 0.014 * w_r
        lp_r2 = 0.978 * lp_r2 + 0.022 * lp_r1
        
        # Realistic sporadic ember crackles (~1.4 crackles per second on average)
        if random.random() < (1.4 / SAMPLE_RATE):
            freq = random.choice([550.0, 950.0, 1500.0, 2400.0])
            length = random.randint(int(SAMPLE_RATE * 0.006), int(SAMPLE_RATE * 0.025))
            pan = random.uniform(0.2, 0.8)
            amp = random.uniform(0.20, 0.60)
            is_wood_snap = random.random() < 0.35
            cracks.append([length, length, freq, pan, amp, is_wood_snap])
            
        crack_l = 0.0
        crack_r = 0.0
        
        active_cracks = []
        for c in cracks:
            rem, total_len, freq, pan, amp, is_wood_snap = c
            t = (total_len - rem) / SAMPLE_RATE
            progress = (total_len - rem) / total_len
            env = math.exp(-14.0 * progress)
            if is_wood_snap:
                # Resonant acoustic wood pop
                s = math.sin(2.0 * math.pi * freq * t) * env * amp
            else:
                # Soft ember sizzle
                s = random.uniform(-1.0, 1.0) * env * amp
            crack_l += s * (1.0 - pan)
            crack_r += s * pan
            
            c[0] -= 1
            if c[0] > 0:
                active_cracks.append(c)
        cracks = active_cracks
        
        samples_l[i] = (lp_l2 * 0.50) + crack_l * 0.5
        samples_r[i] = (lp_r2 * 0.50) + crack_r * 0.5
        
    return samples_l, samples_r

# 4. FOREST BREEZE (Slow, gentle, soothing wind through trees)
def generate_forest_breeze():
    samples_l = [0.0] * TOTAL_SAMPLES
    samples_r = [0.0] * TOTAL_SAMPLES
    
    lp_l1, lp_l2, lp_l3 = 0.0, 0.0, 0.0
    lp_r1, lp_r2, lp_r3 = 0.0, 0.0, 0.0
    
    for i in range(TOTAL_SAMPLES):
        t = i / SAMPLE_RATE
        # Ultra slow, majestic wind gust periods (16s cycle)
        gust1 = 0.50 + 0.35 * math.sin(2.0 * math.pi * (1.0 / 16.0) * t)
        gust2 = 0.15 * math.sin(2.0 * math.pi * (2.0 / 16.0) * t + 0.8)
        wind = max(0.12, min(1.0, gust1 + gust2))
        
        w_l = random.uniform(-1.0, 1.0)
        w_r = random.uniform(-1.0, 1.0)
        
        # Soft low-pass breeze filter (gentle air flow, zero harsh treble)
        f = 0.025 + 0.035 * wind
        lp_l1 = (1.0 - f) * lp_l1 + f * w_l
        lp_l2 = 0.95 * lp_l2 + 0.05 * lp_l1
        lp_l3 = 0.92 * lp_l3 + 0.08 * lp_l2
        
        lp_r1 = (1.0 - f) * lp_r1 + f * w_r
        lp_r2 = 0.95 * lp_r2 + 0.05 * lp_r1
        lp_r3 = 0.92 * lp_r3 + 0.08 * lp_r2
        
        samples_l[i] = lp_l3 * wind * 0.85
        samples_r[i] = lp_r3 * wind * 0.85
        
    return samples_l, samples_r

# 5. OCEAN WAVES (Majestic, slow 16-second rolling ocean wave)
def generate_ocean_waves():
    samples_l = [0.0] * TOTAL_SAMPLES
    samples_r = [0.0] * TOTAL_SAMPLES
    
    b0_l, b1_l, b2_l = 0.0, 0.0, 0.0
    b0_r, b1_r, b2_r = 0.0, 0.0, 0.0
    foam_l, foam_r = 0.0, 0.0
    
    for i in range(TOTAL_SAMPLES):
        t = i / SAMPLE_RATE
        phase = (t / 16.0) % 1.0 # exactly 1 full 16-second majestic wave
        
        # Natural wave cycle:
        # 0.0 -> 0.45: Swell building up slowly from the deep
        # 0.45 -> 0.65: Wave crests and rolls onto the shore
        # 0.65 -> 1.0: Gentle foam dissipates and recedes
        if phase < 0.45:
            p = phase / 0.45
            swell = (math.sin(p * math.pi * 0.5)) ** 2
            foam = 0.05 * p
        elif phase < 0.65:
            p = (phase - 0.45) / 0.20
            swell = math.cos(p * math.pi * 0.5)
            foam = 0.65 * math.sin(p * math.pi) + 0.15
        else:
            p = (phase - 0.65) / 0.35
            swell = 0.10 * (1.0 - p)
            foam = 0.20 * math.exp(-3.0 * p)
            
        w_l = random.uniform(-1.0, 1.0)
        w_r = random.uniform(-1.0, 1.0)
        
        # Deep surf rumble
        b0_l = 0.990 * b0_l + 0.010 * w_l
        b1_l = 0.980 * b1_l + 0.020 * b0_l
        b2_l = 0.970 * b2_l + 0.030 * b1_l
        
        b0_r = 0.990 * b0_r + 0.010 * w_r
        b1_r = 0.980 * b1_r + 0.020 * b0_r
        b2_r = 0.970 * b2_r + 0.030 * b1_r
        
        # Smooth whitewater wash
        foam_l = 0.92 * foam_l + 0.08 * w_l
        foam_r = 0.92 * foam_r + 0.08 * w_r
        
        samples_l[i] = (b2_l * 0.70 * (swell + 0.15)) + (foam_l * 0.30 * foam)
        samples_r[i] = (b2_r * 0.70 * (swell + 0.15)) + (foam_r * 0.30 * foam)
        
    return samples_l, samples_r

if __name__ == "__main__":
    out_dir = "app/src/main/res/raw"
    os.makedirs(out_dir, exist_ok=True)
    
    print("Generating slow, natural 16s ambient soundscapes...")
    
    l, r = generate_brown_noise()
    write_wav(os.path.join(out_dir, "ambient_brown_noise.wav"), l, r)
    
    l, r = generate_rain()
    write_wav(os.path.join(out_dir, "ambient_rain.wav"), l, r)
    
    l, r = generate_fireplace()
    write_wav(os.path.join(out_dir, "ambient_fireplace.wav"), l, r)
    
    l, r = generate_forest_breeze()
    write_wav(os.path.join(out_dir, "ambient_forest_wind.wav"), l, r)
    
    l, r = generate_ocean_waves()
    write_wav(os.path.join(out_dir, "ambient_ocean_waves.wav"), l, r)
    
    print("All 5 soundscapes generated with realistic timing and zero rush!")
