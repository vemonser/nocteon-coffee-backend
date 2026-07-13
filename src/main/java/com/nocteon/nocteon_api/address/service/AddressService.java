package com.nocteon.nocteon_api.address.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.address.dto.request.AddressRequest;
import com.nocteon.nocteon_api.address.dto.response.AddressResponse;
import com.nocteon.nocteon_api.address.entity.Address;
import com.nocteon.nocteon_api.address.repository.AddressRepository;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.exception.notFound.AddressNotFoundException;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    public List<AddressResponse> getAll(UserPrincipal principal) {
        return addressRepository.findByUserId(principal.getUserId())
                .stream().map(this::buildResponse).toList();
    }

    @Transactional
    public AddressResponse create(AddressRequest request, UserPrincipal principal) {
        if (request.isDefault()) {
            clearDefault(principal.getUserId());
        }

        Address address = Address.builder()
                .user(User.builder().id(principal.getUserId()).build())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .postalCode(request.getPostalCode())
                .isDefault(request.isDefault())
                .build();

        return buildResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(Long id, AddressRequest request, UserPrincipal principal) {
        Address address = addressRepository.findByIdAndUserId(id, principal.getUserId())
                .orElseThrow(AddressNotFoundException::new);

        if (request.isDefault()) clearDefault(principal.getUserId());

        address.setFullName(request.getFullName());
        address.setPhone(request.getPhone());
        address.setStreet(request.getStreet());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setCountry(request.getCountry());
        address.setPostalCode(request.getPostalCode());
        address.setDefault(request.isDefault());

        return buildResponse(addressRepository.save(address));
    }

    @Transactional
    public void delete(Long id, UserPrincipal principal) {
        Address address = addressRepository.findByIdAndUserId(id, principal.getUserId())
                .orElseThrow(AddressNotFoundException::new);
        addressRepository.delete(address);
    }

    private void clearDefault(Long userId) {
        addressRepository.findByUserIdAndIsDefault(userId, true)
                .ifPresent(addr -> {
                    addr.setDefault(false);
                    addressRepository.save(addr);
                });
    }

    private AddressResponse buildResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .fullName(address.getFullName())
                .phone(address.getPhone())
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .country(address.getCountry())
                .postalCode(address.getPostalCode())
                .isDefault(address.isDefault())
                .build();
    }
}