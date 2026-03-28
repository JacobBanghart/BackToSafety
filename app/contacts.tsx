/**
 * Emergency Contacts Management Screen
 * Add, edit, and manage emergency contacts for the inner circle
 */

import { useCallback, useEffect, useState } from 'react';
import { useNavigation } from '@react-navigation/native';
import * as Contacts from 'expo-contacts';
import * as Haptics from 'expo-haptics';
import {
  Alert,
  Linking,
  Platform,
  Pressable,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import DraggableFlatList, { RenderItemParams } from '@/utils/draggable-flatlist';

import { AppModal } from '@/components/AppModal';
import { AppTextInput } from '@/components/AppTextInput';
import { ScreenHeader } from '@/components/ScreenHeader';
import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, semantic } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

import { useTheme } from '@/context/ThemeContext';
import { useUnsavedChangesGuard } from '@/hooks/useUnsavedChangesGuard';
import {
  Contact,
  createContact,
  deleteContact,
  getContacts,
  updateContact,
} from '@/database/contacts';
import { formatPhoneInput, normalizeSmsRecipient } from '@/utils/phone';

type ContactRole = 'primary_caregiver' | 'caregiver' | 'neighbor' | 'family' | 'friend' | 'other';

const ROLE_OPTIONS: { value: ContactRole; label: string; icon: string }[] = [
  { value: 'primary_caregiver', label: 'Primary Caregiver', icon: 'star.fill' },
  { value: 'caregiver', label: 'Caregiver', icon: 'heart.fill' },
  { value: 'family', label: 'Family', icon: 'person.2.fill' },
  { value: 'neighbor', label: 'Neighbor', icon: 'house.fill' },
  { value: 'friend', label: 'Friend', icon: 'person.fill' },
  { value: 'other', label: 'Other', icon: 'ellipsis' },
];

interface FormData {
  name: string;
  phone: string;
  relationship: string;
  role: ContactRole;
  address: string;
  notifyOnEmergency: boolean;
  shareMedicalInfo: boolean;
  notes: string;
}

const EMPTY_FORM: FormData = {
  name: '',
  phone: '',
  relationship: '',
  role: 'family',
  address: '',
  notifyOnEmergency: true,
  shareMedicalInfo: false,
  notes: '',
};

export default function ContactsScreen() {
  const navigation = useNavigation();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const [contacts, setContacts] = useState<Contact[]>([]);

  // Modal state
  const [modalVisible, setModalVisible] = useState(false);
  const [modalType, setModalType] = useState<'delete' | 'error' | 'validation' | null>(null);
  const [modalMessage, setModalMessage] = useState('');
  const [pendingDelete, setPendingDelete] = useState<Contact | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [editingContact, setEditingContact] = useState<Contact | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<FormData>(EMPTY_FORM);
  const [initialFormData, setInitialFormData] = useState<FormData>(EMPTY_FORM);
  const [isSaving, setIsSaving] = useState(false);
  const [isImporting, setIsImporting] = useState(false);

  const hasUnsavedFormChanges =
    showForm && JSON.stringify(formData) !== JSON.stringify(initialFormData);

  const loadContacts = useCallback(async () => {
    try {
      const data = await getContacts();
      setContacts(data);
    } catch (error) {
      console.error('Failed to load contacts:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadContacts();
  }, [loadContacts]);

  const showAlert = (type: 'error' | 'validation', message: string) => {
    if (Platform.OS === 'web') {
      setModalType(type);
      setModalMessage(message);
      setModalVisible(true);
    } else {
      Alert.alert(type === 'validation' ? 'Required' : 'Error', message);
    }
  };

  const triggerHaptic = useCallback((style: Haptics.ImpactFeedbackStyle) => {
    if (Platform.OS === 'web') return;
    void Haptics.impactAsync(style).catch(() => undefined);
  }, []);

  const handleSave = async () => {
    if (!formData.name.trim()) {
      showAlert('validation', 'Please enter a name for this contact.');
      return;
    }
    if (!formData.phone.trim()) {
      showAlert('validation', 'Please enter a phone number.');
      return;
    }

    setIsSaving(true);
    try {
      if (editingContact?.id) {
        await updateContact(editingContact.id, {
          name: formData.name,
          phone: formData.phone,
          relationship: formData.relationship || undefined,
          role: formData.role,
          address: formData.address || undefined,
          notifyOnEmergency: formData.notifyOnEmergency,
          shareMedicalInfo: formData.shareMedicalInfo,
          notes: formData.notes || undefined,
        });
      } else {
        const nextSortOrder = contacts.reduce((maxOrder, contact) => {
          return Math.max(maxOrder, contact.sortOrder ?? 0);
        }, -1);

        await createContact({
          name: formData.name,
          phone: formData.phone,
          relationship: formData.relationship || undefined,
          role: formData.role,
          address: formData.address || undefined,
          notifyOnEmergency: formData.notifyOnEmergency,
          shareMedicalInfo: formData.shareMedicalInfo,
          notes: formData.notes || undefined,
          sortOrder: nextSortOrder + 1,
        });
      }

      await loadContacts();
      discardFormAndClose();
    } catch (error) {
      console.error('Failed to save contact:', error);
      showAlert('error', 'Failed to save contact. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleEdit = (contact: Contact) => {
    setEditingContact(contact);
    const nextFormData = {
      name: contact.name,
      phone: contact.phone,
      relationship: contact.relationship || '',
      role: contact.role || 'other',
      address: contact.address || '',
      notifyOnEmergency: contact.notifyOnEmergency,
      shareMedicalInfo: contact.shareMedicalInfo,
      notes: contact.notes || '',
    };
    setFormData(nextFormData);
    setInitialFormData(nextFormData);
    setShowForm(true);
  };

  const handleDelete = (contact: Contact) => {
    if (Platform.OS === 'web') {
      setPendingDelete(contact);
      setModalType('delete');
      setModalMessage(`Are you sure you want to remove ${contact.name}?`);
      setModalVisible(true);
    } else {
      Alert.alert('Delete Contact', `Are you sure you want to remove ${contact.name}?`, [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => confirmDelete(contact),
        },
      ]);
    }
  };

  const confirmDelete = async (contact: Contact) => {
    try {
      if (contact.id) {
        await deleteContact(contact.id);
        await loadContacts();
        if (editingContact?.id === contact.id) {
          discardFormAndClose();
        }
      }
    } catch (error) {
      console.error('Failed to delete contact:', error);
      showAlert('error', 'Failed to delete contact.');
    }
  };

  const handleModalAction = (action: 'confirm' | 'cancel') => {
    setModalVisible(false);
    if (action === 'confirm' && modalType === 'delete' && pendingDelete) {
      confirmDelete(pendingDelete);
    }
    setPendingDelete(null);
    setModalType(null);
    setModalMessage('');
  };

  const handleCall = (phone: string) => {
    Linking.openURL(`tel:${phone}`);
  };

  const handleAddNew = () => {
    setEditingContact(null);
    setFormData(EMPTY_FORM);
    setInitialFormData(EMPTY_FORM);
    setShowForm(true);
  };

  const getContactPhoneNumber = (contact: Contacts.Contact): string => {
    const phoneEntry = contact.phoneNumbers?.find((entry) => {
      const number = entry.number?.trim();
      return typeof number === 'string' && number.length > 0;
    });

    return phoneEntry?.number?.trim() ?? '';
  };

  const getContactName = (contact: Contacts.Contact): string => {
    const fullName = contact.name?.trim();
    if (fullName) {
      return fullName;
    }

    const fallbackName = [contact.firstName, contact.lastName].filter(Boolean).join(' ').trim();
    return fallbackName;
  };

  const toPhoneKey = (phone: string): string => normalizeSmsRecipient(phone).replace(/^\+/, '');

  const handleImportContact = async () => {
    if (Platform.OS === 'web') {
      showAlert('validation', 'Contact import is only available on iPhone and Android devices.');
      return;
    }

    setIsImporting(true);

    try {
      const isAvailable = await Contacts.isAvailableAsync();
      if (!isAvailable) {
        showAlert('error', 'Contact import is not available on this device.');
        return;
      }

      const permission = await Contacts.requestPermissionsAsync();

      if (!permission.granted) {
        Alert.alert(
          'Contacts Permission Needed',
          'Allow contacts access to import an emergency contact from your address book.',
          [
            { text: 'Not Now', style: 'cancel' },
            {
              text: 'Open Settings',
              onPress: () => {
                void Linking.openSettings();
              },
            },
          ],
        );
        return;
      }

      const pickedContact = await Contacts.presentContactPickerAsync();

      if (!pickedContact) {
        return;
      }

      const importedPhone = getContactPhoneNumber(pickedContact);

      if (!importedPhone) {
        showAlert('validation', 'That contact has no phone number to import.');
        return;
      }

      const importedName = getContactName(pickedContact);

      if (!importedName) {
        showAlert('validation', 'That contact has no name to import.');
        return;
      }

      const existingPhoneKeys = new Set(
        contacts
          .map((contact) => toPhoneKey(contact.phone))
          .filter((phoneKey) => phoneKey.length > 0),
      );
      const importedPhoneKey = toPhoneKey(importedPhone);

      if (existingPhoneKeys.has(importedPhoneKey)) {
        showAlert('validation', 'This phone number is already in your emergency contacts.');
        return;
      }

      const nextFormData: FormData = {
        ...EMPTY_FORM,
        name: importedName,
        phone: formatPhoneInput(importedPhone),
      };

      setEditingContact(null);
      setFormData(nextFormData);
      setInitialFormData(nextFormData);
      setShowForm(true);
    } catch (error) {
      console.error('Failed to import contact:', error);
      showAlert('error', 'Could not import contact. Please try again.');
    } finally {
      setIsImporting(false);
    }
  };

  const discardFormAndClose = () => {
    setShowForm(false);
    setEditingContact(null);
    setFormData(EMPTY_FORM);
    setInitialFormData(EMPTY_FORM);
  };

  const handleCancel = () => {
    if (!hasUnsavedFormChanges) {
      discardFormAndClose();
      return;
    }

    Alert.alert('Discard Changes?', 'You have unsaved changes to this contact.', [
      { text: 'Keep Editing', style: 'cancel' },
      {
        text: 'Discard',
        style: 'destructive',
        onPress: discardFormAndClose,
      },
    ]);
  };

  useUnsavedChangesGuard({
    navigation,
    hasUnsavedChanges: hasUnsavedFormChanges,
    isSaving,
    title: 'Discard Changes?',
    message: 'You have unsaved changes to this contact.',
    confirmLabel: 'Discard',
    onDiscard: discardFormAndClose,
  });

  const getRoleInfo = (role?: ContactRole) => {
    return ROLE_OPTIONS.find((r) => r.value === role) || ROLE_OPTIONS[5];
  };

  const handleDragEnd = async ({ data }: { data: Contact[] }) => {
    const updates = data
      .map((contact, index) => ({
        id: contact.id,
        previousSortOrder: contact.sortOrder,
        newSortOrder: index,
      }))
      .filter((contact) => contact.id && contact.previousSortOrder !== contact.newSortOrder);

    const reordered = data.map((contact, index) => ({
      ...contact,
      sortOrder: index,
    }));
    setContacts(reordered);

    if (updates.length === 0) {
      return;
    }

    try {
      await Promise.all(
        updates.map((contact) => updateContact(contact.id!, { sortOrder: contact.newSortOrder })),
      );
      await loadContacts();
    } catch (error) {
      console.error('Failed to reorder contacts:', error);
      showAlert('error', 'Failed to save contact order. Please try again.');
      await loadContacts();
    }
  };

  const renderContactCard = ({ item: contact, drag, isActive }: RenderItemParams<Contact>) => {
    const roleInfo = getRoleInfo(contact.role);

    return (
      <Pressable
        onLongPress={drag}
        delayLongPress={180}
        style={[
          styles.contactCard,
          isActive && styles.contactCardActive,
          {
            backgroundColor: isActive ? theme.primaryLight : theme.card,
            borderColor: isActive ? theme.primary : theme.border,
          },
        ]}
      >
        {contact.notifyOnEmergency && (
          <View
            style={[
              styles.emergencyBadgeTop,
              { backgroundColor: theme.primaryLight, borderColor: theme.primary },
            ]}
          >
            <IconSymbol name="checkmark.circle.fill" size={11} color={theme.primary} />
            <ThemedText style={[styles.emergencyBadgeText, { color: theme.primary }]}>
              IN ALERT CIRCLE
            </ThemedText>
          </View>
        )}

        <View style={styles.contactHeader}>
          <View style={styles.contactInfo}>
            <ThemedText style={styles.contactName}>{contact.name}</ThemedText>
            <ThemedText style={[styles.contactMeta, { color: theme.textSecondary }]}>
              {roleInfo.label}
              {contact.relationship ? ` • ${contact.relationship}` : ''}
            </ThemedText>
          </View>

          <View style={styles.contactActions}>
            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: semantic.success }]}
              onPress={() => handleCall(contact.phone)}
            >
              <IconSymbol name="phone.fill" size={16} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: theme.primary }]}
              onPress={() => handleEdit(contact)}
            >
              <IconSymbol name="pencil" size={16} color="#fff" />
            </TouchableOpacity>
          </View>
        </View>

        {contact.address && (
          <View style={styles.contactDetails}>
            <View style={styles.detailRow}>
              <IconSymbol name="location" size={14} color={theme.icon} />
              <ThemedText style={[styles.detailText, { color: theme.text }]} numberOfLines={2}>
                {contact.address}
              </ThemedText>
            </View>
          </View>
        )}
      </Pressable>
    );
  };

  const renderForm = () => (
    <ScrollView style={styles.formContainer} showsVerticalScrollIndicator={false}>
      <ThemedText style={styles.formTitle}>
        {editingContact ? 'Edit Contact' : 'Add Emergency Contact'}
      </ThemedText>

      {/* Name */}
      <AppTextInput
        label="Name"
        value={formData.name}
        onChangeText={(text) => setFormData({ ...formData, name: text })}
        placeholder="Contact name"
        required
      />

      {/* Phone */}
      <AppTextInput
        label="Phone Number"
        value={formData.phone}
        onChangeText={(text) => setFormData({ ...formData, phone: formatPhoneInput(text) })}
        placeholder="(555) 123-4567"
        keyboardType="phone-pad"
        autoComplete="tel"
        required
      />

      {/* Relationship */}
      <AppTextInput
        label="Relationship"
        value={formData.relationship}
        onChangeText={(text) => setFormData({ ...formData, relationship: text })}
        placeholder="e.g., Daughter, Next-door neighbor"
      />

      {/* Address */}
      <AppTextInput
        label="Address"
        value={formData.address}
        onChangeText={(text) => setFormData({ ...formData, address: text })}
        placeholder="Street address, city, state"
        multiline
        numberOfLines={2}
      />

      {/* Role */}
      <View style={styles.formField}>
        <ThemedText style={[styles.fieldLabel, { color: theme.text }]}>Role</ThemedText>
        <View style={styles.roleGrid}>
          {ROLE_OPTIONS.map((option) => {
            const isSelected = formData.role === option.value;
            return (
              <Pressable
                key={option.value}
                hitSlop={6}
                style={[
                  styles.roleOption,
                  {
                    backgroundColor: isSelected ? theme.primary : theme.inputBackground,
                    borderColor: isSelected ? theme.primary : theme.inputBorder,
                  },
                ]}
                onPress={() =>
                  setFormData((prev) =>
                    prev.role === option.value ? prev : { ...prev, role: option.value },
                  )
                }
              >
                <IconSymbol
                  name={option.icon as any}
                  size={14}
                  color={isSelected ? '#fff' : theme.icon}
                />
                <ThemedText
                  style={[
                    styles.roleOptionText,
                    isSelected ? { color: '#fff' } : { color: theme.text },
                  ]}
                >
                  {option.label}
                </ThemedText>
              </Pressable>
            );
          })}
        </View>
      </View>

      {/* Notify on Emergency */}
      <TouchableOpacity
        style={[
          styles.toggleField,
          {
            backgroundColor: theme.inputBackground,
            borderColor: theme.inputBorder,
          },
        ]}
        onPress={() => setFormData({ ...formData, notifyOnEmergency: !formData.notifyOnEmergency })}
      >
        <View style={styles.toggleInfo}>
          <ThemedText style={styles.toggleLabel}>Notify in Emergency</ThemedText>
          <ThemedText style={[styles.toggleHint, { color: theme.textSecondary }]}>
            Adds this contact to your Alert Circle (people who get emergency SMS alerts)
          </ThemedText>
        </View>
        <View
          style={[
            styles.toggle,
            {
              backgroundColor: formData.notifyOnEmergency ? semantic.success : theme.border,
            },
          ]}
        >
          <View
            style={[styles.toggleKnob, formData.notifyOnEmergency && styles.toggleKnobActive]}
          />
        </View>
      </TouchableOpacity>

      {/* Notes */}
      <AppTextInput
        label="Notes"
        value={formData.notes}
        onChangeText={(text) => setFormData({ ...formData, notes: text })}
        placeholder="Additional notes about this contact..."
        multiline
        numberOfLines={3}
      />

      {editingContact && (
        <TouchableOpacity
          style={[styles.formDeleteButton, { borderColor: semantic.error }]}
          onPress={() => handleDelete(editingContact)}
        >
          <IconSymbol name="trash" size={14} color={semantic.error} />
          <ThemedText style={[styles.formDeleteText, { color: semantic.error }]}>
            Delete Contact
          </ThemedText>
        </TouchableOpacity>
      )}
      <View style={{ height: 40 }} />
    </ScrollView>
  );

  const renderContactList = () => (
    <DraggableFlatList
      data={contacts}
      keyExtractor={(contact: Contact) => String(contact.id ?? contact.phone)}
      onDragEnd={handleDragEnd}
      onDragBegin={() => triggerHaptic(Haptics.ImpactFeedbackStyle.Medium)}
      onRelease={() => triggerHaptic(Haptics.ImpactFeedbackStyle.Light)}
      activationDistance={8}
      autoscrollThreshold={120}
      containerStyle={styles.listContainer}
      contentContainerStyle={[
        styles.listContent,
        contacts.length === 0 && styles.emptyListContainer,
      ]}
      showsVerticalScrollIndicator={false}
      renderItem={renderContactCard}
      ListHeaderComponent={
        contacts.length > 0 ? (
          <View style={styles.listHeader}>
            <ThemedText style={[styles.listCount, { color: theme.textSecondary }]}>
              {contacts.length} contact{contacts.length !== 1 ? 's' : ''}
            </ThemedText>
            <ThemedText style={[styles.listHint, { color: theme.textSecondary }]}>
              Press and hold any contact card to reorder emergency priority.
            </ThemedText>
          </View>
        ) : null
      }
      ListEmptyComponent={
        <View style={styles.emptyState}>
          <View style={[styles.emptyIconWrap, { backgroundColor: theme.primaryLight }]}>
            <IconSymbol name="person.2.fill" size={40} color={theme.primary} />
          </View>
          <ThemedText type="title" style={[styles.emptyTitle, { color: theme.text }]}>
            No Contacts Yet
          </ThemedText>
          <ThemedText style={[styles.emptyText, { color: theme.textSecondary }]}>
            Add family members, neighbors, or friends who can help in an emergency.
          </ThemedText>
          <View style={styles.emptyActionsRow}>
            <Pressable
              style={[styles.addButton, styles.emptyAction, { backgroundColor: theme.primary }]}
              onPress={handleAddNew}
            >
              <IconSymbol name="plus" size={20} color="#fff" />
              <ThemedText style={styles.addButtonText} numberOfLines={1}>
                Add Contact
              </ThemedText>
            </Pressable>
            <Pressable
              style={[
                styles.importButton,
                styles.emptyAction,
                { backgroundColor: theme.card, borderColor: theme.border },
              ]}
              onPress={handleImportContact}
              disabled={isImporting}
            >
              <IconSymbol name="square.and.arrow.down" size={18} color={theme.tint} />
              <ThemedText
                style={[styles.importButtonText, { color: theme.text }]}
                numberOfLines={1}
              >
                {isImporting ? 'Importing...' : 'Import Contact'}
              </ThemedText>
            </Pressable>
          </View>
        </View>
      }
      ListFooterComponent={
        <>
          {contacts.length > 0 && (
            <View style={styles.footerActionsRow}>
              <TouchableOpacity
                style={[styles.addButton, styles.footerAction, { backgroundColor: theme.primary }]}
                onPress={handleAddNew}
              >
                <IconSymbol name="plus" size={20} color="#fff" />
                <ThemedText style={styles.addButtonText}>Add Contact</ThemedText>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  styles.importButton,
                  styles.footerAction,
                  { backgroundColor: theme.card, borderColor: theme.border },
                ]}
                onPress={handleImportContact}
                disabled={isImporting}
              >
                <IconSymbol name="square.and.arrow.down" size={18} color={theme.tint} />
                <ThemedText style={[styles.importButtonText, { color: theme.text }]}>
                  {isImporting ? 'Importing...' : 'Import Contact'}
                </ThemedText>
              </TouchableOpacity>
            </View>
          )}
          <View style={{ height: 40 }} />
        </>
      }
    />
  );

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea} edges={['top']}>
        {/* Header */}
        <ScreenHeader
          title={
            showForm ? (editingContact ? 'Edit Contact' : 'Add Contact') : 'Emergency Contacts'
          }
          onBack={showForm ? handleCancel : undefined}
          rightElement={
            showForm ? (
              <Pressable
                onPress={handleSave}
                style={[styles.headerSaveButton, { backgroundColor: theme.tint }]}
                disabled={isSaving}
              >
                <ThemedText style={styles.headerSaveText} numberOfLines={1}>
                  {isSaving ? 'Saving...' : editingContact ? 'Update' : 'Add'}
                </ThemedText>
              </Pressable>
            ) : undefined
          }
        />

        {/* Content */}
        {isLoading ? (
          <View style={styles.loading}>
            <ThemedText>Loading...</ThemedText>
          </View>
        ) : showForm ? (
          renderForm()
        ) : (
          renderContactList()
        )}
      </SafeAreaView>

      <AppModal
        visible={modalVisible}
        onDismiss={() => handleModalAction('cancel')}
        title={
          modalType === 'delete'
            ? 'Delete Contact'
            : modalType === 'validation'
              ? 'Required'
              : 'Error'
        }
        message={modalMessage}
        type={modalType === 'delete' ? 'delete' : 'alert'}
        onConfirm={() => handleModalAction('confirm')}
      />
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  loading: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerSaveButton: {
    minWidth: 72,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
    borderRadius: Radius.md,
    alignItems: 'center',
  },
  headerSaveText: {
    color: '#fff',
    ...Typography.bodyBold,
  },

  // List styles
  listContainer: {
    flex: 1,
  },
  listContent: {
    padding: Spacing.lg,
    paddingBottom: 0,
    flexGrow: 1,
  },
  emptyListContainer: {
    justifyContent: 'flex-start',
  },
  listHeader: {
    marginBottom: Spacing.md,
  },
  listCount: {
    ...Typography.body,
  },
  listHint: {
    ...Typography.caption,
    marginTop: Spacing.xxs,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 60,
    paddingHorizontal: Spacing.xxl,
    gap: Spacing.md,
  },
  emptyIconWrap: {
    width: 88,
    height: 88,
    borderRadius: 44,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: Spacing.sm,
  },
  emptyTitle: {
    textAlign: 'center',
  },
  emptyText: {
    ...Typography.body,
    textAlign: 'center',
  },
  emptyActionsRow: {
    flexDirection: 'row',
    width: '100%',
    gap: Spacing.sm,
    marginTop: Spacing.sm,
    maxWidth: 560,
  },
  emptyAction: {
    flex: 1,
  },

  // Contact card
  contactCard: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.lg,
    marginBottom: Spacing.md,
  },
  contactCardActive: {
    opacity: 0.98,
    transform: [{ scale: 1.02 }],
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.2,
    shadowRadius: 10,
    elevation: 5,
  },
  contactHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.sm,
  },
  contactInfo: {
    flex: 1,
    paddingRight: Spacing.md,
  },
  contactName: {
    ...Typography.bodyLarge,
    fontWeight: '600',
  },
  contactMeta: {
    ...Typography.caption,
    marginTop: 4,
  },
  emergencyBadgeTop: {
    position: 'absolute',
    top: -10,
    right: Spacing.md,
    zIndex: 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: Spacing.sm,
    paddingVertical: 3,
    borderRadius: Radius.md,
    borderWidth: 1,
  },
  emergencyBadgeText: {
    ...Typography.small,
    fontWeight: '700',
    fontSize: 10,
    lineHeight: 12,
    letterSpacing: 0.5,
  },
  contactActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
    alignItems: 'center',
  },
  actionButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    justifyContent: 'center',
    alignItems: 'center',
  },
  contactDetails: {
    gap: 6,
  },
  detailRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
  },
  detailText: {
    ...Typography.body,
    flex: 1,
  },
  formDeleteButton: {
    marginTop: Spacing.md,
    borderWidth: 1,
    borderRadius: Radius.md,
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.xs,
    paddingHorizontal: Spacing.md,
  },
  formDeleteText: {
    ...Typography.bodyBold,
  },
  addButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
    padding: Spacing.lg,
    borderRadius: Radius.lg,
  },
  addButtonText: {
    color: '#fff',
    ...Typography.bodyBold,
  },
  footerActionsRow: {
    flexDirection: 'row',
    gap: Spacing.sm,
    marginTop: Spacing.sm,
  },
  footerAction: {
    flex: 1,
  },
  importButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
    padding: Spacing.lg,
    borderRadius: Radius.lg,
    borderWidth: 1,
  },
  importButtonText: {
    ...Typography.bodyBold,
  },

  // Form styles
  formContainer: {
    flex: 1,
    padding: Spacing.lg,
  },
  formTitle: {
    ...Typography.headline,
    marginBottom: Spacing.xl,
  },
  formField: {
    marginBottom: Spacing.lg,
  },
  fieldLabel: {
    ...Typography.bodyBold,
    marginBottom: Spacing.xs,
  },
  roleGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  roleOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderRadius: Radius.md,
    borderWidth: 1,
  },
  roleOptionText: {
    ...Typography.caption,
    fontWeight: '500',
  },
  toggleField: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: Spacing.md,
    borderRadius: Radius.lg,
    borderWidth: 1,
    marginBottom: Spacing.lg,
  },
  toggleInfo: {
    flex: 1,
    marginRight: Spacing.md,
  },
  toggleLabel: {
    ...Typography.bodyLarge,
    fontWeight: '500',
  },
  toggleHint: {
    ...Typography.caption,
    marginTop: Spacing.xxs,
  },
  toggle: {
    width: 50,
    height: 30,
    borderRadius: 15,
    padding: 3,
  },
  toggleKnob: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#fff',
  },
  toggleKnobActive: {
    transform: [{ translateX: 20 }],
  },
});
