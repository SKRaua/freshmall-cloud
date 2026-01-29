package com.freshmall.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.freshmall.user.service.UserService;
import com.freshmall.common.entity.User;
import com.freshmall.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    UserMapper userMapper;

    @Value("${File.uploadPath}")
    private String uploadPath;

    @Override
    public List<User> getUserList(String keyword) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.like("username", keyword);
        }
        queryWrapper.orderBy(true, false, "create_time");
        return userMapper.selectList(queryWrapper);
    }

    @Override
    public User getAdminUser(User user) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", user.getUsername());
        queryWrapper.eq("password", user.getPassword());
        queryWrapper.gt("role", "1"); // 大于1
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public User getNormalUser(User user) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", user.getUsername());
        queryWrapper.eq("password", user.getPassword());
        queryWrapper.eq("role", "1");
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public void createUser(User user) {
        if (user.getAvatarFile() != null) {
            String avatar = saveAvatar(user.getAvatarFile());
            if (StringUtils.isNotBlank(avatar)) {
                user.setAvatar(avatar);
            }
        }
        user.setRole(String.valueOf(User.NormalUser));
        user.setStatus("0");
        userMapper.insert(user);
    }

    @Override
    public void deleteUser(String id) {
        userMapper.deleteById(id);
    }

    @Override
    public void updateUser(User user) {
        if (user.getAvatarFile() != null) {
            String avatar = saveAvatar(user.getAvatarFile());
            if (StringUtils.isNotBlank(avatar)) {
                user.setAvatar(avatar);
            }
        }
        userMapper.updateById(user);
    }

    @Override
    public User getUserByToken(String token) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("token", token);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public User getUserDetail(String userId) {
        return userMapper.selectById(userId);
    }

    private String saveAvatar(MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            try {
                String oldFileName = file.getOriginalFilename();
                String randomStr = UUID.randomUUID().toString();
                String newFileName = randomStr + "_" + oldFileName;
                File dest = new File(uploadPath + File.separator + "avatar" + File.separator + newFileName);
                if (!dest.getParentFile().exists()) {
                    dest.getParentFile().mkdirs();
                }
                file.transferTo(dest);
                return newFileName;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }
}
