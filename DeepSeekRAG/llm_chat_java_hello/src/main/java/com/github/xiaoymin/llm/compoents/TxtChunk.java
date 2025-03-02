package com.github.xiaoymin.llm.compoents;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.xiaoymin.llm.domain.llm.ChunkResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Peiyao Zhao
 * 2025/03/02 17:48
 * @since llm_chat_java_hello
 */
@Slf4j
@Component
@AllArgsConstructor
public class TxtChunk {

    public List<ChunkResult> chunk(String docId){
        if(docId.endsWith(".pdf")){
            return chunkPDF(docId);
        }else if(docId.endsWith(".txt")){
            return chunkTxt(docId);
        }else {
            log.error("不支持的文件类型: {}", docId);
        }
        return new ArrayList<>();
    }
    public List<ChunkResult> chunkPDF(String docId){
        String path = "data/" + docId;
        log.info("start chunk---> docId:{}, path:{}", docId, path);
        ClassPathResource classPathResource = new ClassPathResource(path);
        try {
            // 利用 PDFBox 读取 PDF 文档
            RandomAccessReadBuffer rarBuffer = new RandomAccessReadBuffer(IOUtils.toByteArray(classPathResource.getInputStream()));
            PDDocument document = Loader.loadPDF(rarBuffer);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();

            // 按固定字数分割，例如每256个字符一块
            String[] chunks = StrUtil.split(text, 256);
            log.info("chunk size: {}", ArrayUtil.length(chunks));
            List<ChunkResult> results = new ArrayList<>();
            AtomicInteger atomicInteger = new AtomicInteger(0);
            for (String chunk : chunks) {
                ChunkResult chunkResult = new ChunkResult();
                chunkResult.setDocId(docId);
                chunkResult.setContent(chunk);
                chunkResult.setChunkId(atomicInteger.incrementAndGet());
                results.add(chunkResult);
            }
            return results;
        } catch (IOException e) {
            log.error("PDF 分块错误: {}", e.getMessage());
        }
        return new ArrayList<>();
    }
    public List<ChunkResult> chunkTxt(String docId){
        String path="data/"+docId;
        log.info("start chunk---> docId:{},path:{}",docId,path);
        ClassPathResource classPathResource=new ClassPathResource(path);
        try {
            String txt=IoUtil.read(classPathResource.getInputStream(), StandardCharsets.UTF_8);
            //按固定字数分割,256
            String[] lines=StrUtil.split(txt,256);
            log.info("chunk size:{}", ArrayUtil.length(lines));
            List<ChunkResult> results=new ArrayList<>();
            AtomicInteger atomicInteger=new AtomicInteger(0);
            for (String line:lines){
                ChunkResult chunkResult=new ChunkResult();
                chunkResult.setDocId(docId);
                chunkResult.setContent(line);
                chunkResult.setChunkId(atomicInteger.incrementAndGet());
                results.add(chunkResult);
            }
            return results;
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return new ArrayList<>();
    }

}
