#!/bin/bash
#
# TextSwap 模型下载脚本
# 运行: bash download_models.sh
#
set -euo pipefail

MODEL_DIR="app/src/main/assets/models"
mkdir -p "$MODEL_DIR"

echo "=== TextSwap 模型下载 ==="

# ── LaMa-Tiny ONNX (inpainting) ──
# 来源: Hugging Face - spinagon/lama-tiny-onnx
LAMA_URL="https://huggingface.co/spinagon/lama-tiny-onnx/resolve/main/lama_tiny_fp32.onnx"
LAMA_FILE="$MODEL_DIR/lama_tiny.onnx"

if [ -f "$LAMA_FILE" ]; then
    echo "[SKIP] $LAMA_FILE 已存在"
else
    echo "[下载] LaMa-Tiny ONNX (~160MB)..."
    if command -v wget &>/dev/null; then
        wget -O "$LAMA_FILE" "$LAMA_URL"
    elif command -v curl &>/dev/null; then
        curl -L -o "$LAMA_FILE" "$LAMA_URL"
    else
        echo "[ERROR] 需要 wget 或 curl 才能下载模型"
        exit 1
    fi
    echo "[OK] $LAMA_FILE"
fi

# ── Font Classifier TFLite ──
# 来源: 自定义训练，此处使用公开字体分类模型替代
# 实际使用时替换为真实训练产物，或从项目release下载
FONT_URL="https://github.com/textswap-ai/models/releases/download/v1.0/font_classifier.tflite"
FONT_FILE="$MODEL_DIR/font_classifier.tflite"

if [ -f "$FONT_FILE" ]; then
    echo "[SKIP] $FONT_FILE 已存在"
else
    echo "[WARN] 字体分类模型需自行准备。"
    echo "      途径1: 从项目releases下载预训练模型"
    echo "      途径2: 使用TrainFontClassifier.ipynb自行训练"
    echo "      （Fallback: FontMatcher 自动使用像素分析兜底，无需模型也可运行）"
    echo "      模型放置路径: $FONT_FILE"
fi

echo ""
echo "=== 下载完成 ==="
echo "LaMa inpainting: $([ -f "$LAMA_FILE" ] && echo '已就绪' || echo '需下载')"
echo "Font classifier: $([ -f "$FONT_FILE" ] && echo '已就绪' || echo '可选（fallback可用）')"
echo ""
echo "提示: 即使模型未就绪，App 仍可正常运行（使用传统算法兜底）。"