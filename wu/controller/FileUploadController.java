package com.jeeplus.modules.infinibase.wu.controller;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.jeeplus.modules.infinibase.wu.entity.FileInfo;
import com.jeeplus.modules.infinibase.wu.service.webUploader;


import java.io.*;

@Controller
@RequestMapping("${adminPath}/webUp")
public class FileUploadController {

	private final static Logger log = LoggerFactory.getLogger(FileUploadController.class);

	//文件上传目录，最好写在配置文件里
	private String uploadFolder="G:/code";

	@Autowired
	private webUploader wu;


	//大文件上传，负责把每个分片上传到目的路径
	@RequestMapping(value = "fileUpload", method = RequestMethod.POST)
	@ResponseBody
	public String fileUpload(String status, FileInfo info, @RequestParam(value = "file", required = false) MultipartFile file){

		if(status == null){	//文件上传
			if(file != null && !file.isEmpty()){	//验证请求不会包含数据上传，所以避免NullPoint这里要检查一下file变量是否为null
				try {
					File target = wu.getReadySpace(info, this.uploadFolder);	//为上传的文件准备好对应的位置
					if(target == null){
						return "{\"status\": 0, \"message\": \"" + wu.getErrorMsg() + "\"}";
					}

					file.transferTo(target);	//保存上传文件

					//将MD5签名和合并后的文件path存入持久层，注意这里这个需求导致需要修改webuploader.js源码3170行
					//因为原始webuploader.js不支持为formData设置函数类型参数，这将导致不能在控件初始化后修改该参数
					if(info.getChunks() <= 0){
						if(!wu.saveMd52FileMap(info.getMd5(), target.getName())){
							log.error("文件[" + info.getMd5() + "=>" + target.getName() + "]保存关系到持久成失败，但并不影响文件上传，只会导致日后该文件可能被重复上传而已");
						}
					}

					return "{\"status\": 1, \"path\": \"" + target.getName() + "\"}";

				}catch(IOException ex){
					log.error("数据上传失败", ex);
					return "{\"status\": 0, \"message\": \"数据上传失败\"}";
				}
			}
		}
		log.error("请求参数不完整");
		return "{\"status\": 0, \"message\": \"请求参数不完整\"}";
	}
	
	
	/**
	 * 
	 * <p>Title: check</br>
	 * <p>Description: 上传前的验证，以及合并</br>
	 * @param status
	 * @param info
	 * @return
	 */
	@RequestMapping(value = "check", method = RequestMethod.POST)
	@ResponseBody
	public String check(String status,FileInfo info){

		if(status.equals("md5Check")){	//秒传验证

			String path = wu.md5Check(info.getMd5());

			if(path == null){
				return "{\"ifExist\": 0}";
			}else{
				return "{\"ifExist\": 1, \"path\": \"" + path + "\"}";
			}

		}else if(status.equals("chunkCheck")){	//分块验证

			//检查目标分片是否存在且完整
			if(wu.chunkCheck(this.uploadFolder + "/" + info.getName() + "/" + info.getChunkIndex(), Long.valueOf(info.getSize()))){
				return "{\"ifExist\": 1}";
			}else{
				return "{\"ifExist\": 0}";
			}

		}else if(status.equals("chunksMerge")){	//分块合并

			String path = wu.chunksMerge(info.getName(), info.getExt(), info.getChunks(), info.getMd5(), this.uploadFolder);
			if(path == null){
				return "{\"status\": 0, \"message\": \"" + wu.getErrorMsg() + "\"}";
			}

			return "{\"status\": 1, \"path\": \"" + path + "\", \"message\": \"中文测试\"}";
		}
		return "{\"status\": 0, \"message\": \"请求参数不完整\"}";
	
		
	}
}